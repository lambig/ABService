package com.abservice.infrastructure.audio;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Linuxの専用一時領域をプロセス単位で専有する。予約した最大byte数をsnapshot削除まで保持する。
 * 起動時清掃は専有後、所有する名前の通常ファイルだけを対象とし、未知の内容があれば開始しない。
 */
public final class AudioTemporaryStore implements AutoCloseable {
    private static final String LOCK = ".audio-owner.lock";
    private static final AtomicReference<Set<Path>> OWNERS = new AtomicReference<>(Set.of());
    private final Path directory;
    private final FileChannel owner;
    private final long capacity;
    private final AtomicLong reserved = new AtomicLong();
    private final AtomicInteger snapshots = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    private AudioTemporaryStore(
            Path directory,
            FileChannel owner,
            long capacity) {
        this.directory = directory;
        this.owner = owner;
        this.capacity = capacity;
    }

    /** 専用のPOSIXディレクトリを作成・検査し、残骸を清掃してから受入可能にする。 */
    public static synchronized AudioTemporaryStore open(Path directory, long capacity) throws IOException {
        Objects.requireNonNull(directory);
        require(capacity > 0, "Invalid audio temporary capacity");
        final var root = prepare(directory);
        require(OWNERS.get().stream().noneMatch(root::equals), "Audio temporary directory is in use");
        final var channel = FileChannel.open(
                lockFile(root),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try {
            Optional.ofNullable(channel.tryLock())
                    .orElseThrow(() -> new IOException("Audio temporary directory is in use"));
            clean(root);
            return register(
                    root,
                    channel,
                    capacity);
        } catch (OverlappingFileLockException failure) {
            closeAfterFailure(channel, failure);
            throw new IOException("Audio temporary directory is in use", failure);
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(channel, failure);
            throw failure;
        }
    }

    private static Path lockFile(Path root) throws IOException {
        final var file = root.resolve(LOCK);
        require(
                Files.notExists(file, LinkOption.NOFOLLOW_LINKS)
                        ? true
                        : Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS),
                "Invalid audio lock file");
        return file;
    }

    /** 同一JVMで同じlockファイルを再open/closeして既存のPOSIXロックを落とさない。 */
    private static AudioTemporaryStore register(
            Path root,
            FileChannel channel,
            long capacity) {
        OWNERS.updateAndGet(
                roots -> Stream.concat(roots.stream(), Stream.of(root))
                        .collect(Collectors.toUnmodifiableSet()));
        return new AudioTemporaryStore(
                root,
                channel,
                capacity);
    }

    private static Path prepare(Path directory) throws IOException {
        final var root = directory.toAbsolutePath().normalize();
        Files.createDirectories(
                root,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        require(root.equals(root.toRealPath()), "Audio temporary directory must not use symbolic links");
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS), "Invalid audio temporary directory");
        require(
                PosixFilePermissions.fromString("rwx------")
                        .containsAll(Files.getPosixFilePermissions(root)),
                "Audio temporary directory must be owner-only");
        return root;
    }

    @SuppressWarnings("PMD.SingleUseLocalVariable") // RESOURCE-LIFETIME: listのstreamは暗黙のcloseまで所有する。
    private static List<Path> entries(Path root) throws IOException {
        try (var files = Files.list(root)) {
            return files.filter(Predicate.not(file -> file.getFileName().toString().equals(LOCK)))
                    .limit(1025).toList();
        }
    }

    private static void clean(Path root) throws IOException {
        final var entries = entries(root);
        require(entries.size() <= 1024, "Too many audio temporary remnants");
        for (final var entry : entries) {
            require(
                    entry.getFileName().toString()
                            .matches("flac-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.tmp"),
                    "Unexpected audio temporary entry");
            require(Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS), "Unsafe audio temporary entry");
        }
        for (final var entry : entries) {
            Files.delete(entry);
        }
    }

    /** 入力を読み始める前に最大サイズを予約する。待ち行列を作らない。 */
    synchronized Lease allocate(long maxBytes) throws IOException {
        require(owner.isOpen(), "Audio temporary store is closed");
        require(maxBytes > 0, "Invalid audio snapshot reservation");
        require(maxBytes <= capacity - reserved.get(), "Audio temporary capacity exhausted");
        require(snapshots.get() < 128, "Too many audio snapshots");
        return reserve(
                new Lease(
                        this,
                        Files.createFile(
                                directory.resolve("flac-" + UUID.randomUUID() + ".tmp"),
                                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))),
                        maxBytes));
    }

    private Lease reserve(Lease lease) {
        reserved.addAndGet(lease.bytes);
        snapshots.incrementAndGet();
        return lease;
    }

    private synchronized void release(long bytes) {
        reserved.addAndGet(-bytes);
        snapshots.decrementAndGet();
    }

    /** 生存中のsnapshotがあれば専有を解放しない。失敗した削除は再試行してから閉じる。 */
    @Override
    public synchronized void close() throws IOException {
        require(snapshots.get() == 0, "Audio snapshots are still active");
        closed.set(
                closed.get()
                        ? true
                        : closeAndRelease());
    }

    private boolean closeAndRelease() throws IOException {
        owner.close();
        OWNERS.updateAndGet(
                roots -> roots.stream().filter(Predicate.not(directory::equals))
                        .collect(Collectors.toUnmodifiableSet()));
        return true;
    }

    private static void require(boolean condition, String message) throws IOException {
        Optional.of(condition)
                .filter(Boolean::booleanValue)
                .orElseThrow(() -> new IOException(message));
    }

    private static void closeAfterFailure(FileChannel channel, Exception failure) {
        try {
            channel.close();
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    static final class Lease implements AutoCloseable {
        private final AudioTemporaryStore store;
        private final Path file;
        private final long bytes;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger readers = new AtomicInteger();

        private Lease(
                AudioTemporaryStore store,
                Path file,
                long bytes) {
            this.store = store;
            this.file = file;
            this.bytes = bytes;
        }

        Path file() {
            return file;
        }

        synchronized InputStream openStream() throws IOException {
            require(Boolean.FALSE.equals(closed.get()), "Audio snapshot is closed");
            return trackReader(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS));
        }

        private InputStream trackReader(InputStream input) {
            readers.incrementAndGet();
            return new LeasedInput(input, this);
        }

        @Override
        public synchronized void close() throws IOException {
            closed.set(
                    closed.get()
                            ? true
                            : deleteAndRelease());
        }

        private boolean deleteAndRelease() throws IOException {
            require(readers.get() == 0, "Audio snapshot streams are still active");
            Files.deleteIfExists(file);
            store.release(bytes);
            return true;
        }
    }

    private static final class LeasedInput extends FilterInputStream {
        private final Lease lease;
        private final AtomicBoolean closed = new AtomicBoolean();

        private LeasedInput(InputStream input, Lease lease) {
            super(input);
            this.lease = lease;
        }

        @Override
        public synchronized void close() throws IOException {
            closed.set(
                    closed.get()
                            ? true
                            : closeAndRelease());
        }

        private boolean closeAndRelease() throws IOException {
            super.close();
            lease.readers.decrementAndGet();
            return true;
        }
    }
}
