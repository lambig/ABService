package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioStorage;
import com.abservice.infrastructure.persistence.repository.DatabasePrivateAudioRegistrations;
import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@QuarkusTest
@ExtendWith(CleanDatabase.class)
class PrivateAudioRuntimeIntegrationTest {
    private static final FlacMetadata METADATA = new FlacMetadata(
            100,
            "a".repeat(64),
            44100,
            2,
            16,
            44100);
    @Inject
    private PrivateAudioRuntime disabled;
    @Inject
    private DatabasePrivateAudioRegistrations registrations;
    @Inject
    private Mutiny.SessionFactory sessions;
    @Inject
    private Vertx vertx;
    @TempDir
    private Path directory;
    private final AtomicReference<Optional<PrivateAudioRuntime>> active = new AtomicReference<>(Optional.empty());
    private final AtomicInteger ownerClosed = new AtomicInteger();

    @AfterEach
    void closeRuntime() throws Exception {
        for (final var runtime : active.get().stream().toList()) {
            runtime.close();
            runtime.close();
            assertThat(ownerClosed.get()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("既定設定では起動しても全入口を拒否し入力を取得しない")
    @RunOnVertxContext
    void staysDisabledByDefault(UniAsserter asserter) {
        asserter.assertFailedWith(() -> disabled.ingest(UUID.randomUUID(), () -> {
            throw new AssertionError("Disabled input must not open");
        }), IOException.class);
        asserter.assertFailedWith(() -> disabled.recover(UUID.randomUUID()), IOException.class);
        asserter.assertFailedWith(disabled::maintain, IOException.class);
    }

    @Test
    @DisplayName("公開画像と同じバケットを指定すると資源の生成前に起動を拒否する")
    void rejectsSharedPublicBucket() {
        final var path = directory.resolve("must-not-create");
        final var producer = new PrivateAudioRuntimeProducer(
                new Configuration(path),
                registrations,
                registrations,
                vertx,
                "synthetic-private-audio",
                "us-east-1",
                false,
                Optional.empty());
        assertThatThrownBy(producer::runtime).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A separate private audio bucket is required");
        assertThat(path).doesNotExist();
    }

    @Test
    @DisplayName("期限付きの入力から実FLAC検査とDB確定へ接続し一時実体を解放する")
    @RunOnVertxContext
    void ingestsNativeFlacThroughRuntime(UniAsserter asserter) throws Exception {
        final byte[] bytes = fixture();
        final var storage = new Storage();
        final var runtime = open(storage);
        final var id = UUID.randomUUID();
        asserter.execute(() -> registrations.create(id, Instant.now().plusSeconds(600)));
        asserter.assertThat(() -> runtime.ingest(id, () -> new ByteArrayInputStream(bytes)), metadata -> {
            assertThat(metadata.byteLength()).isEqualTo(bytes.length);
            assertThat(metadata.totalSamples()).isEqualTo(4096);
            assertThat(storage.writes.get()).isEqualTo(1);
        });
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Confirmed(storage.stored.get().orElseThrow())));
        asserter.execute(() -> {
            try (var files = Files.list(directory.resolve("snapshots"))) {
                assertThat(files.map(path -> path.getFileName().toString())).containsExactly(".audio-owner.lock");
            } catch (IOException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    @Test
    @DisplayName("未保存の古い検査記録を終了し遅れて保存された実体を再PUTせず明示復旧する")
    @RunOnVertxContext
    void recoversLateStorageAfterAbandonment(UniAsserter asserter) throws Exception {
        final var storage = new Storage();
        final var runtime = open(storage);
        final var id = UUID.randomUUID();
        asserter.execute(() -> inspected(id));
        asserter.execute(this::age);
        asserter.assertThat(runtime::maintain, changed -> assertThat(changed).isEqualTo(1));
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Abandoned(METADATA)));
        asserter.assertFailedWith(() -> runtime.recover(id), IOException.class);
        asserter.execute(() -> storage.stored.set(Optional.of(METADATA)));
        asserter.assertThat(() -> runtime.recover(id), metadata -> assertThat(metadata).isEqualTo(METADATA));
        asserter.assertThat(() -> registrations.find(id), row -> {
            assertThat(row.orElseThrow().state()).isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA));
            assertThat(storage.writes.get()).isZero();
        });
    }

    @Test
    @DisplayName("HEAD障害・不一致は検査記録を維持し、一致が確認できてから確定する")
    @RunOnVertxContext
    void preservesUnknownStorageState(UniAsserter asserter) throws Exception {
        final var storage = new Storage();
        final var runtime = open(storage);
        final var id = UUID.randomUUID();
        asserter.execute(() -> inspected(id));
        asserter.execute(this::age);
        storage.failure.set(Optional.of(new IOException("synthetic HEAD failure")));
        asserter.assertFailedWith(runtime::maintain, IOException.class);
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Inspected(METADATA)));
        asserter.execute(() -> {
            storage.failure.set(Optional.empty());
            storage.stored.set(
                    Optional.of(
                            new FlacMetadata(
                                    101,
                                    "b".repeat(64),
                                    44100,
                                    2,
                                    16,
                                    44100)));
        });
        asserter.assertFailedWith(runtime::maintain, IOException.class);
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Inspected(METADATA)));
        asserter.execute(() -> storage.stored.set(Optional.of(METADATA)));
        asserter.assertThat(runtime::maintain, changed -> assertThat(changed).isEqualTo(1));
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA)));
    }

    @Test
    @DisplayName("処理中の停止は資源を解放せず新規処理を拒否し、完了後に安全に閉じる")
    @RunOnVertxContext
    void stopsAdmissionBeforeClosingOwners(UniAsserter asserter) throws Exception {
        final var storage = new GatedStorage();
        final var runtime = open(storage);
        final var id = UUID.randomUUID();
        final var completed = new CompletableFuture<FlacMetadata>();
        asserter.execute(() -> inspected(id));
        asserter.execute(
                () -> runtime.recover(id).subscribe().with(completed::complete, completed::completeExceptionally));
        asserter.execute(() -> onContext(storage.entered));
        asserter.execute(() -> {
            assertThatThrownBy(runtime::close).isInstanceOf(IOException.class).hasMessageContaining("still active");
            assertThat(ownerClosed.get()).isZero();
        });
        asserter.assertFailedWith(runtime::maintain, IOException.class);
        asserter.execute(() -> storage.release.complete(true));
        asserter.assertThat(
                () -> Uni.createFrom().completionStage(completed),
                metadata -> assertThat(metadata).isEqualTo(METADATA));
        asserter.assertFailedWith(runtime::maintain, IOException.class);
    }

    @Test
    @DisplayName("HEAD中に別実行者が受付終了した登録を定期照合で自動復旧しない")
    @RunOnVertxContext
    void keepsConcurrentAbandonmentExplicit(UniAsserter asserter) throws Exception {
        final var storage = new GatedStorage();
        final var runtime = open(storage);
        final var id = UUID.randomUUID();
        final var completed = new CompletableFuture<Integer>();
        asserter.execute(() -> inspected(id));
        asserter.execute(this::age);
        asserter.execute(
                () -> runtime.maintain().subscribe().with(completed::complete, completed::completeExceptionally));
        asserter.execute(() -> onContext(storage.entered));
        asserter.assertThat(
                () -> registrations.abandonInspected(id, Duration.ofHours(24)),
                changed -> assertThat(changed).isTrue());
        asserter.execute(() -> storage.release.complete(true));
        asserter.assertThat(() -> onContext(completed), changed -> assertThat(changed).isZero());
        asserter.assertThat(
                () -> registrations.find(id),
                row -> assertThat(row.orElseThrow().state())
                        .isEqualTo(new PrivateAudioRegistration.Abandoned(METADATA)));
        asserter.assertThat(() -> runtime.recover(id), metadata -> assertThat(metadata).isEqualTo(METADATA));
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: workerのfixture通知後に元のevent-loopへ戻す。
    private static <T> Uni<T> onContext(CompletableFuture<T> future) {
        final var context = Vertx.currentContext();
        return Uni.createFrom().completionStage(future).emitOn(command -> context.runOnContext(_ -> command.run()));
    }

    private PrivateAudioRuntime open(PrivateAudioStorage storage) throws IOException {
        active.set(
                Optional.of(
                        PrivateAudioRuntime.open(
                                vertx,
                                new Configuration(directory.resolve("snapshots")),
                                registrations,
                                registrations,
                                storage,
                                ownerClosed::incrementAndGet)));
        return active.get().orElseThrow();
    }

    private Uni<Boolean> inspected(UUID id) {
        return registrations.create(id, Instant.now().plusSeconds(600))
                .chain(() -> registrations.recordInspection(id, METADATA));
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: 固定fixture更新のcommitはAPIへ任せる。
    private Uni<Integer> age() {
        return sessions.withTransaction((session, _) -> session.createNativeQuery("""
                UPDATE private_audio_registration SET created_at = clock_timestamp() - interval '3 days',
                    expires_at = clock_timestamp() - interval '2 days'
                """).executeUpdate());
    }

    private byte[] fixture() throws Exception {
        final var raw = directory.resolve("tone.raw");
        final var flac = directory.resolve("tone.flac");
        Files.write(raw, new byte[4096 * 4]);
        final var process = new ProcessBuilder("flac", "--silent", "--force-raw-format", "--endian=little",
                "--sign=signed", "--channels=2", "--bps=16", "--sample-rate=44100", "--no-padding",
                "--no-seektable", "--output-name=" + flac, raw.toString()).start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            return Files.readAllBytes(flac);
        } finally {
            process.destroyForcibly().onExit().join();
        }
    }

    private record Configuration(Path path) implements PrivateAudioConfig {
        @Override
        public String enabled() {
            return "true";
        }
        @Override
        public Optional<String> bucket() {
            return Optional.of("synthetic-private-audio");
        }
        @Override
        public Optional<String> temporaryDirectory() {
            return Optional.of(path.toString());
        }
        @Override
        public long temporaryBytes() {
            return 536870912L;
        }
        @Override
        public Duration inputTimeout() {
            return Duration.ofSeconds(5);
        }
        @Override
        public Duration retention() {
            return Duration.ofHours(24);
        }
        @Override
        public Duration maintenanceInterval() {
            return Duration.ofMinutes(15);
        }
        @Override
        public Duration downloadUrlExpiry() {
            return Duration.ofMinutes(10);
        }
    }

    private static final class GatedStorage implements PrivateAudioStorage {
        private final CompletableFuture<Boolean> entered = new CompletableFuture<>();
        private final CompletableFuture<Boolean> release = new CompletableFuture<>();

        @Override
        public void write(UUID id, InspectedAudio audio) {
            throw new AssertionError("Recovery must not write");
        }

        @Override
        public Optional<FlacMetadata> find(UUID id) throws IOException {
            entered.complete(true);
            try {
                release.get(10, TimeUnit.SECONDS);
                return Optional.of(METADATA);
            } catch (Exception failure) {
                throw new IOException(failure);
            }
        }
    }

    private static final class Storage implements PrivateAudioStorage {
        private final AtomicReference<Optional<FlacMetadata>> stored = new AtomicReference<>(Optional.empty());
        private final AtomicReference<Optional<IOException>> failure = new AtomicReference<>(Optional.empty());
        private final AtomicInteger writes = new AtomicInteger();

        @Override
        public void write(UUID id, InspectedAudio snapshot) {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            stored.set(Optional.of(snapshot.metadata()));
            writes.incrementAndGet();
        }

        @Override
        public Optional<FlacMetadata> find(UUID id) throws IOException {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            for (final var error : failure.get().stream().toList()) {
                throw error;
            }
            return stored.get();
        }
    }
}
