package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("非公開音源の専用一時領域・容量予約・起動時清掃")
class AudioTemporaryStoreTest {
    private static final String STALE = "flac-00000000-0000-4000-8000-000000000001.tmp";
    @TempDir
    private Path directory;

    @Test
    @DisplayName("最大サイズを削除まで予約し、二重closeで容量を増やさない")
    void reservesUntilDeleted() throws Exception {
        try (var store = AudioTemporaryStore.open(directory.resolve("audio"), 10)) {
            final var first = store.allocate(7);
            Files.write(first.file(), new byte[]{1});
            assertThatThrownBy(() -> store.allocate(4)).isInstanceOf(IOException.class);
            try (var second = store.allocate(3)) {
                assertThat(second.file()).exists();
                first.close();
                first.close();
                assertThatThrownBy(() -> store.allocate(8)).isInstanceOf(IOException.class);
                try (var third = store.allocate(7)) {
                    assertThat(third.file()).exists();
                }
            }
            try (var all = store.allocate(10)) {
                assertThat(Files.getPosixFilePermissions(all.file()))
                        .isEqualTo(PosixFilePermissions.fromString("rw-------"));
            }
        }
    }

    @Test
    @DisplayName("snapshotと開いた読込streamが残る間は専有と容量を解放しない")
    void retainsOwnershipWhileActive() throws Exception {
        final var root = directory.resolve("audio");
        try (var store = AudioTemporaryStore.open(root, 10); var lease = store.allocate(10)) {
            assertThatThrownBy(store::close).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
            try (var stream = lease.openStream()) {
                assertThat(stream.read()).isEqualTo(-1);
                assertThatThrownBy(lease::close).isInstanceOf(IOException.class);
                assertThatThrownBy(() -> store.allocate(1)).isInstanceOf(IOException.class);
            }
        }
        try (var reopened = AudioTemporaryStore.open(root, 10); var lease = reopened.allocate(10)) {
            assertThat(lease.file()).exists();
        }
    }

    @Test
    @DisplayName("専有後に既知の残骸だけを清掃する")
    void cleansAbandonedSnapshots() throws Exception {
        final var root = privateDirectory();
        Files.write(root.resolve(STALE), new byte[]{1, 2, 3});
        try (var store = AudioTemporaryStore.open(root, 10); var lease = store.allocate(10)) {
            assertThat(root.resolve(STALE)).doesNotExist();
            assertThat(lease.file()).exists();
        }
    }

    @Test
    @DisplayName("未知のファイルがある場合は既知の残骸も消さず開始を拒否する")
    void refusesMixedContents() throws Exception {
        final var root = privateDirectory();
        Files.writeString(root.resolve("unrelated.txt"), "synthetic unrelated content");
        Files.write(root.resolve(STALE), new byte[]{1});
        assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
        assertThat(root.resolve("unrelated.txt")).hasContent("synthetic unrelated content");
        assertThat(root.resolve(STALE)).exists();
        Files.delete(root.resolve("unrelated.txt"));
        try (var store = AudioTemporaryStore.open(root, 10); var lease = store.allocate(1)) {
            assertThat(lease.file()).exists();
        }
    }

    @Test
    @DisplayName("清掃名のsymlinkも辿らず対象外ファイルを保全する")
    void refusesSymbolicRemnants() throws Exception {
        final var root = privateDirectory();
        final var outside = Files.writeString(directory.resolve("outside"), "keep");
        Files.createSymbolicLink(root.resolve(STALE), outside);
        assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
        assertThat(outside).hasContent("keep");
        assertThat(Files.isSymbolicLink(root.resolve(STALE))).isTrue();
    }

    @Test
    @DisplayName("symlinkのroot・lockや他ユーザーへ開いたディレクトリを拒否する")
    void refusesUnsafeDirectoryAndLock() throws Exception {
        final var root = privateDirectory();
        final var alias = Files.createSymbolicLink(directory.resolve("alias"), root);
        assertThatThrownBy(() -> AudioTemporaryStore.open(alias, 10)).isInstanceOf(IOException.class);
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        final var outside = Files.writeString(directory.resolve("outside"), "keep");
        Files.createSymbolicLink(root.resolve(".audio-owner.lock"), outside);
        assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
        assertThat(outside).hasContent("keep");
    }

    @Test
    @DisplayName("削除失敗では容量を返却せず修復後のcloseを再試行できる")
    void retainsReservationOnDeleteFailure() throws Exception {
        try (var store = AudioTemporaryStore.open(directory.resolve("audio"), 10)) {
            final var lease = store.allocate(10);
            Files.delete(lease.file());
            Files.createDirectory(lease.file());
            final var child = Files.writeString(lease.file().resolve("block-delete"), "synthetic obstacle");
            assertThatThrownBy(lease::close).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> store.allocate(1)).isInstanceOf(IOException.class);
            assertThatThrownBy(store::close).isInstanceOf(IOException.class);
            Files.delete(child);
            lease.close();
            try (var retry = store.allocate(10)) {
                assertThat(retry.file()).exists();
            }
        }
    }

    @Test
    @DisplayName("閉鎖後の割当・読込と不正な容量を拒否する")
    void refusesInvalidOrClosedAllocation() throws Exception {
        final var root = directory.resolve("audio");
        assertThatThrownBy(() -> AudioTemporaryStore.open(root, 0)).isInstanceOf(IOException.class);
        try (var store = AudioTemporaryStore.open(root, Long.MAX_VALUE)) {
            assertThatThrownBy(() -> store.allocate(0)).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> store.allocate(-1)).isInstanceOf(IOException.class);
            final var lease = store.allocate(Long.MAX_VALUE);
            assertThatThrownBy(() -> store.allocate(1)).isInstanceOf(IOException.class);
            lease.close();
            assertThatThrownBy(lease::openStream).isInstanceOf(IOException.class);
            store.close();
            assertThatThrownBy(() -> store.allocate(1)).isInstanceOf(IOException.class);
            try (var replacement = AudioTemporaryStore.open(root, 10)) {
                store.close();
                assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
                try (var current = replacement.allocate(10)) {
                    assertThat(current.file()).exists();
                }
            }
        }
    }

    @Test
    @DisplayName("別JVMが専有中なら残骸清掃を開始しない")
    void excludesAnotherProcess() throws Exception {
        final var root = privateDirectory();
        Files.write(root.resolve(STALE), new byte[]{1});
        final var source = Files.writeString(directory.resolve("LockOwner.java"), """
                import java.nio.channels.*;
                import java.nio.file.*;
                class LockOwner {
                    public static void main(String[] args) throws Exception {
                        try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE); var lock = channel.lock()) {
                            System.out.println("locked");
                            System.out.flush();
                            System.in.read();
                        }
                    }
                }
                """);
        final var child = new ProcessBuilder(
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        "java").toString(),
                source.toString(),
                root.resolve(".audio-owner.lock").toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            assertThat(CompletableFuture.supplyAsync(() -> {
                try {
                    return child.inputReader().readLine();
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }).get(20, TimeUnit.SECONDS)).isEqualTo("locked");
            assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
            assertThat(root.resolve(STALE)).exists();
        } finally {
            child.destroyForcibly();
            assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
        }
        try (var store = AudioTemporaryStore.open(root, 10); var lease = store.allocate(10)) {
            assertThat(root.resolve(STALE)).doesNotExist();
            assertThat(lease.file()).exists();
        }
    }

    private Path privateDirectory() throws IOException {
        return Files.createDirectory(
                directory.resolve("audio"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    @Test
    @DisplayName("容量が余っていてもsnapshotの件数を制限する")
    void boundsSnapshotCount() throws Exception {
        try (var store = AudioTemporaryStore.open(directory.resolve("audio"), 1000)) {
            final var leases = Stream.generate(() -> {
                try {
                    return store.allocate(1);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }).limit(128).toList();
            try {
                assertThatThrownBy(() -> store.allocate(1)).isInstanceOf(IOException.class)
                        .hasMessage("Too many audio snapshots");
            } finally {
                for (final var lease : leases) {
                    lease.close();
                }
            }
        }
    }

    @Test
    @DisplayName("lockファイルがディレクトリの場合は清掃前に拒否する")
    void rejectsInvalidLockType() throws Exception {
        final var root = privateDirectory();
        Files.createDirectory(root.resolve(".audio-owner.lock"));
        Files.write(root.resolve(STALE), new byte[]{1});
        assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class)
                .hasMessage("Invalid audio lock file");
        assertThat(root.resolve(STALE)).exists();
    }

    @Test
    @DisplayName("同一JVMの二重open拒否後も別プロセスへの排他を維持する")
    void duplicateOpenDoesNotDropOperatingSystemLock() throws Exception {
        final var root = privateDirectory();
        final var source = Files.writeString(directory.resolve("LockProbe.java"), """
                import java.nio.channels.*;
                import java.nio.file.*;
                class LockProbe {
                    public static void main(String[] args) throws Exception {
                        try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE)) {
                            try (var lock = channel.tryLock()) {
                                System.out.println(lock == null ? "busy" : "acquired");
                            }
                        }
                    }
                }
                """);
        try (var store = AudioTemporaryStore.open(root, 10)) {
            assertThatThrownBy(() -> AudioTemporaryStore.open(root, 10)).isInstanceOf(IOException.class);
            final var child = new ProcessBuilder(
                    Path.of(
                            System.getProperty("java.home"),
                            "bin",
                            "java").toString(),
                    source.toString(),
                    root.resolve(".audio-owner.lock").toString()).redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            try {
                assertThat(child.waitFor(20, TimeUnit.SECONDS)).isTrue();
                assertThat(child.exitValue()).isZero();
                assertThat(child.inputReader().readLine()).isEqualTo("busy");
            } finally {
                child.destroyForcibly();
                assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
            }
            try (var lease = store.allocate(10)) {
                assertThat(lease.file()).exists();
            }
        }
    }
}
