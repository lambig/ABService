package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.FlacInspector;
import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.AudioOperationConflictException;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioConflictException;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.port.PrivateAudioStorage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("非公開音源の検査・保存・DB確定と復旧")
@SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: Vert.xの完了通知引数はAPIへ任せる。
class PrivateAudioIngestionTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final FlacMetadata METADATA = new FlacMetadata(
            3,
            "a".repeat(64),
            44100,
            2,
            16,
            44100);
    private static final FlacMetadata OTHER = new FlacMetadata(
            4,
            "b".repeat(64),
            48000,
            1,
            24,
            48000);
    private final Vertx vertx = Vertx.vertx();
    private final Database database = new Database();
    private final Storage storage = new Storage();
    private final Snapshot snapshot = new Snapshot();
    private final AtomicInteger inputClosed = new AtomicInteger();
    private final AtomicInteger sourceOpened = new AtomicInteger();
    private final FlacInspector inspector = (source, limits) -> {
        assertThat(Context.isOnEventLoopThread()).isFalse();
        assertThat(source.readAllBytes()).containsExactly(
                1,
                2,
                3);
        return snapshot;
    };

    @AfterEach
    void closeVertx() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private PrivateAudioIngestion ingestion(int attempts) {
        return new PrivateAudioIngestion(
                database,
                inspector,
                storage,
                1,
                attempts);
    }

    private InputStream input() {
        assertThat(Context.isOnEventLoopThread()).isFalse();
        sourceOpened.incrementAndGet();
        return new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override
            public void close() {
                assertThat(Context.isOnEventLoopThread()).isFalse();
                inputClosed.incrementAndGet();
            }
        };
    }

    private <T> CompletableFuture<T> start(Supplier<Uni<T>> work) {
        final var result = new CompletableFuture<T>();
        vertx.runOnContext(_ -> work.get().subscribe().with(result::complete, result::completeExceptionally));
        return result;
    }

    private CompletableFuture<FlacMetadata> ingest(PrivateAudioIngestion service) {
        return start(
                () -> service.ingest(
                        ID,
                        this::input,
                        FlacInspectionLimits.defaults()));
    }

    @Test
    @DisplayName("先行commitの応答までPUTを待ち、DBは元context・I/Oはworkerで実行する")
    void waitsForInspectionCommit() throws Exception {
        database.commit = new CompletableFuture<>();
        final var result = ingest(ingestion(2));
        database.recorded.get(5, TimeUnit.SECONDS);
        assertThat(storage.writes.get()).isZero();
        assertThat(result).isNotDone();
        vertx.runOnContext(_ -> database.commit.complete(true));
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(METADATA);
        assertThat(database.state.get()).isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA));
        assertThat(storage.lastSnapshot.get()).isSameAs(snapshot);
        assertThat(snapshot.closed.get()).isEqualTo(1);
        assertThat(inputClosed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("検査不合格は入力を閉じ、DB記録・PUTを開始しない")
    void inspectionFailure() {
        final var service = new PrivateAudioIngestion(database, (source, limits) -> {
            throw new IOException("invalid synthetic FLAC");
        }, storage, 1, 2);
        assertThatThrownBy(() -> ingest(service).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
        assertThat(inputClosed.get()).isEqualTo(1);
        assertThat(database.recorded).isNotDone();
        assertThat(storage.writes.get()).isZero();
    }

    @Test
    @DisplayName("DBの検査記録が拒否・失敗ならsnapshotを解放して保存しない")
    void inspectionCommitFailure() {
        database.commit = CompletableFuture.failedFuture(new IOException("database unavailable"));
        assertThatThrownBy(() -> ingest(ingestion(2)).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
        assertThat(storage.writes.get()).isZero();
        assertThat(snapshot.closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("検査記録の競合で負けた呼出しは保存せずsnapshotを解放する")
    void inspectionCommitConflict() {
        database.commit = CompletableFuture.completedFuture(false);
        assertThatThrownBy(() -> ingest(ingestion(2)).get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AudioOperationConflictException.class);
        assertThat(storage.writes.get()).isZero();
        assertThat(snapshot.closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("確定済み・失効済み・検査済み登録へ入力を再取得しない")
    void rejectsNonPendingInput() {
        for (final var state : List.of(
                new PrivateAudioRegistration.Confirmed(METADATA),
                new PrivateAudioRegistration.Inspected(METADATA),
                new PrivateAudioRegistration.Expired())) {
            database.state.set(state);
            assertThatThrownBy(() -> ingest(ingestion(2)).get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(AudioOperationConflictException.class);
        }
        assertThat(sourceOpened.get()).isZero();
        assertThat(storage.writes.get()).isZero();
    }

    @Test
    @DisplayName("入力のclose失敗でも検査済みsnapshotを解放しDB記録へ進まない")
    void closesSnapshotWhenInputCloseFails() {
        final var service = ingestion(2);
        assertThatThrownBy(
                () -> start(
                        () -> service.ingest(
                                ID,
                                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}) {
                                    @Override
                                    public void close() throws IOException {
                                        throw new IOException("synthetic close failure");
                                    }
                                },
                                FlacInspectionLimits.defaults()))
                        .get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IOException.class);
        assertThat(snapshot.closed.get()).isEqualTo(1);
        assertThat(storage.writes.get()).isZero();
    }

    @Test
    @DisplayName("PUT成功後の応答喪失は同じ実測値をHEADで照合して確定する")
    void lostWriteResponse() throws Exception {
        storage.loseResponse = true;
        assertThat(ingest(ingestion(2)).get(5, TimeUnit.SECONDS)).isEqualTo(METADATA);
        assertThat(storage.writes.get()).isEqualTo(1);
        assertThat(snapshot.closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("未存在を確認できた書込失敗だけ同じsnapshotで上限まで再試行する")
    void boundedRetry() throws Exception {
        storage.failedWrites.set(1);
        assertThat(ingest(ingestion(2)).get(5, TimeUnit.SECONDS)).isEqualTo(METADATA);
        assertThat(storage.writes.get()).isEqualTo(2);
        assertThat(storage.lastSnapshot.get()).isSameAs(snapshot);
    }

    @Test
    @DisplayName("再試行上限で止まり検査記録を残してsnapshotを解放する")
    void exhaustedRetry() {
        storage.failedWrites.set(10);
        assertThatThrownBy(() -> ingest(ingestion(2)).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
        assertThat(storage.writes.get()).isEqualTo(2);
        assertThat(database.state.get()).isInstanceOf(PrivateAudioRegistration.Inspected.class);
        assertThat(snapshot.closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ID競合後にHEADが未存在でも再PUTしない")
    void doesNotRetryConflict() {
        storage.failedWrites.set(3);
        storage.writeFailure = new PrivateAudioConflictException(new IOException("synthetic conflict"));
        assertThatThrownBy(() -> ingest(ingestion(3)).get(5, TimeUnit.SECONDS)).hasCause(storage.writeFailure);
        assertThat(storage.writes.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("割込みのI/O失敗を再試行しない")
    void doesNotRetryInterruption() {
        storage.failedWrites.set(3);
        storage.writeFailure = new InterruptedIOException("synthetic interruption");
        assertThatThrownBy(() -> ingest(ingestion(3)).get(5, TimeUnit.SECONDS)).hasCause(storage.writeFailure);
        assertThat(storage.writes.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Vert.xイベントループ外の入口を入力取得前に拒否する")
    void requiresEventLoop() {
        assertThatThrownBy(() -> ingestion(2).recover(ID).await().atMost(Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sourceOpened.get()).isZero();
    }

    @Test
    @DisplayName("HEAD失敗では未存在と推測してPUT・確定しない")
    void unavailableHead() {
        storage.headFailure = true;
        assertThatThrownBy(() -> ingest(ingestion(3)).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
        assertThat(storage.writes.get()).isZero();
        assertThat(database.confirmCalls.get()).isZero();
        assertThat(snapshot.closed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("保存実体が検査記録と異なれば上書き・確定しない")
    void mismatchedObject() {
        storage.stored.set(OTHER);
        assertThatThrownBy(() -> ingest(ingestion(2)).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
        assertThat(storage.writes.get()).isZero();
        assertThat(database.confirmCalls.get()).isZero();
    }

    @Test
    @DisplayName("保存後のDB障害から入力を再取得せずHEAD照合だけで復旧する")
    void recoverAfterDatabaseFailure() throws Exception {
        final var service = ingestion(2);
        database.confirmFailure = true;
        assertThatThrownBy(() -> ingest(service).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
        assertThat(snapshot.closed.get()).isEqualTo(1);
        database.confirmFailure = false;
        assertThat(start(() -> service.recover(ID)).get(5, TimeUnit.SECONDS)).isEqualTo(METADATA);
        assertThat(sourceOpened.get()).isEqualTo(1);
        assertThat(storage.writes.get()).isEqualTo(1);
        assertThatThrownBy(() -> start(() -> service.recover(ID)).get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AudioOperationConflictException.class);
    }

    @Test
    @DisplayName("復旧で実体が未存在ならPUTせず検査済みのまま失敗する")
    void recoveryNeedsObject() {
        database.state.set(new PrivateAudioRegistration.Inspected(METADATA));
        assertThatThrownBy(() -> start(() -> ingestion(2).recover(ID)).get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IOException.class);
        assertThat(storage.writes.get()).isZero();
        assertThat(sourceOpened.get()).isZero();
    }

    @Test
    @DisplayName("購読取消でもPUT完了前に枠やsnapshotを解放せず、混雑時に入力を開かない")
    void cancellationDoesNotReleaseRunningWork() throws Exception {
        storage.writeGate = new CountDownLatch(1);
        final var service = ingestion(2);
        final var subscription = new CompletableFuture<Cancellable>();
        vertx.runOnContext(
                _ -> subscription.complete(
                        service.ingest(
                                ID,
                                this::input,
                                FlacInspectionLimits.defaults())
                                .subscribe().with(item -> {
                                }, failure -> {
                                })));
        storage.writeStarted.get(5, TimeUnit.SECONDS);
        subscription.get(5, TimeUnit.SECONDS).cancel();
        assertThatThrownBy(() -> ingest(service).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
        assertThat(sourceOpened.get()).isEqualTo(1);
        assertThat(snapshot.closed.get()).isZero();
        storage.writeGate.countDown();
        snapshot.closedSignal.get(5, TimeUnit.SECONDS);
        assertThat(database.state.get()).isEqualTo(new PrivateAudioRegistration.Confirmed(METADATA));
    }

    private static final class Snapshot implements InspectedAudio {
        private final AtomicInteger closed = new AtomicInteger();
        private final CompletableFuture<Boolean> closedSignal = new CompletableFuture<>();

        @Override
        public FlacMetadata metadata() {
            return METADATA;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        }

        @Override
        public void close() {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            closed.incrementAndGet();
            closedSignal.complete(true);
        }
    }

    private final class Storage implements PrivateAudioStorage {
        private final AtomicReference<FlacMetadata> stored = new AtomicReference<>();
        private final AtomicReference<InspectedAudio> lastSnapshot = new AtomicReference<>();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger failedWrites = new AtomicInteger();
        private final CompletableFuture<Boolean> writeStarted = new CompletableFuture<>();
        private CountDownLatch writeGate = new CountDownLatch(0);
        private boolean loseResponse;
        private boolean headFailure;
        private IOException writeFailure = new IOException("synthetic transport failure");

        @Override
        public void write(UUID id, InspectedAudio audio) throws IOException {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            assertThat(database.state.get()).isEqualTo(new PrivateAudioRegistration.Inspected(METADATA));
            lastSnapshot.set(audio);
            writes.incrementAndGet();
            writeStarted.complete(true);
            try {
                assertThat(writeGate.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException(failure);
            }
            Optional.of(failedWrites.getAndDecrement()).filter(count -> count <= 0)
                    .orElseThrow(() -> writeFailure);
            stored.set(audio.metadata());
            Optional.of(loseResponse).filter(value -> !value).orElseThrow(() -> new IOException("response lost"));
        }

        @Override
        public Optional<FlacMetadata> find(UUID id) throws IOException {
            assertThat(Context.isOnEventLoopThread()).isFalse();
            Optional.of(headFailure).filter(value -> !value).orElseThrow(() -> new IOException("HEAD access denied"));
            return Optional.ofNullable(stored.get());
        }
    }

    private static final class Database implements PrivateAudioRegistrations {
        private final AtomicReference<PrivateAudioRegistration.State> state = new AtomicReference<>(
                new PrivateAudioRegistration.Pending());
        private final AtomicReference<Context> context = new AtomicReference<>();
        private final CompletableFuture<Boolean> recorded = new CompletableFuture<>();
        private final AtomicInteger confirmCalls = new AtomicInteger();
        private CompletableFuture<Boolean> commit = CompletableFuture.completedFuture(true);
        private boolean confirmFailure;

        private void assertContext() {
            assertThat(Context.isOnEventLoopThread()).isTrue();
            context.compareAndSet(null, Vertx.currentContext());
            assertThat(Vertx.currentContext()).isSameAs(context.get());
        }

        @Override
        public Uni<Boolean> create(UUID id, Instant expiresAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Uni<Optional<PrivateAudioRegistration>> find(UUID id) {
            assertContext();
            return Uni.createFrom().item(
                    Optional.of(
                            new PrivateAudioRegistration(id, Instant.EPOCH,
                                    Instant.EPOCH.plusSeconds(1), state.get())));
        }

        @Override
        public Uni<Boolean> recordInspection(UUID id, FlacMetadata metadata) {
            assertContext();
            state.set(new PrivateAudioRegistration.Inspected(metadata));
            recorded.complete(true);
            return Uni.createFrom().completionStage(commit);
        }

        @Override
        public Uni<Boolean> confirm(UUID id, FlacMetadata metadata) {
            assertContext();
            confirmCalls.incrementAndGet();
            final var previous = state.get();
            return confirmFailure
                    ? Uni.createFrom().failure(new IOException("confirmation database unavailable"))
                    : Uni.createFrom().item(
                            previous instanceof PrivateAudioRegistration.Inspected
                                    && state.compareAndSet(previous, new PrivateAudioRegistration.Confirmed(metadata)));
        }

        @Override
        public Uni<List<UUID>> expirePending(int batchSize) {
            throw new UnsupportedOperationException();
        }
    }
}
