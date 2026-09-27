package com.abservice.infrastructure.audio;

import com.abservice.application.port.PrivateAudioOperations;
import com.abservice.application.port.AudioOperationConflictException;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.PrivateAudioMaintenance;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.port.PrivateAudioStorage;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.jboss.logging.Logger;

/** 既定無効の単一実行入口。取消後も処理終了まで枠と資源を保持し、終了開始後は新規処理を拒否する。 */
public final class PrivateAudioRuntime implements AutoCloseable, PrivateAudioOperations {
    private static final Logger LOG = Logger.getLogger(PrivateAudioRuntime.class);
    private final Vertx vertx;
    private final Optional<Resources> resources;
    private final Semaphore admission = new Semaphore(1);
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong timer = new AtomicLong(-1);

    private PrivateAudioRuntime(Vertx vertx, Optional<Resources> resources) {
        this.vertx = Objects.requireNonNull(vertx);
        this.resources = resources;
    }

    static PrivateAudioRuntime disabled(Vertx vertx) {
        return new PrivateAudioRuntime(vertx, Optional.empty());
    }

    static PrivateAudioRuntime open(Vertx vertx, PrivateAudioConfig config, PrivateAudioRegistrations registrations,
            PrivateAudioMaintenance maintenance, PrivateAudioStorage storage, AutoCloseable storageOwner)
            throws IOException {
        PrivateAudioRuntimeProducer.validate(config);
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(registrations);
        Objects.requireNonNull(maintenance);
        Objects.requireNonNull(storage);
        Objects.requireNonNull(storageOwner);
        final var temporary = AudioTemporaryStore.open(
                Path.of(config.temporaryDirectory().orElseThrow()),
                config.temporaryBytes());
        final var inputs = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), Thread.ofPlatform().daemon().name("audio-input-", 0).factory());
        try {
            return new PrivateAudioRuntime(vertx, Optional.of(
                    new Resources(
                            temporary, inputs,
                            new PrivateAudioIngestion(registrations, new NativeFlacInspector("flac", temporary),
                                    storage, 1,
                                    2),
                            new PrivateAudioReconciliation(
                                    registrations,
                                    maintenance,
                                    storage,
                                    config.retention()),
                            config.inputTimeout(), config.maintenanceInterval(), storageOwner)));
        } catch (RuntimeException failure) {
            inputs.shutdownNow();
            try {
                temporary.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public Uni<FlacMetadata> ingest(UUID id, Callable<InputStream> source) {
        return admitted(
                active -> active.ingestion.ingest(
                        id,
                        () -> new DeadlineAudioInput(
                                source,
                                active.inputTimeout,
                                active.inputs),
                        FlacInspectionLimits.defaults()));
    }

    public Uni<FlacMetadata> recover(UUID id) {
        return admitted(active -> active.reconciliation.recover(id));
    }

    public Uni<Integer> maintain() {
        return admitted(active -> active.reconciliation.maintain(10));
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // LIFECYCLE-GUARD: 資源の内容ではなく所有者の停止・timer状態で生成を制御する。
    synchronized void start() {
        resources.filter(active -> Boolean.FALSE.equals(stopping.get()))
                .filter(active -> timer.get() < 0)
                .ifPresent(active -> timer.set(vertx.setPeriodic(active.interval.toMillis(), this::tick)));
    }

    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // REACTIVE-API: callbackの通知引数に処理対象は含まれない。
    private void tick(long timerId) {
        Optional.of(timerId)
                .filter(value -> value == timer.get()).ifPresent(
                        value -> VertxContext.getOrCreateDuplicatedContext(vertx.getOrCreateContext())
                                .runOnContext(ignored -> {
                                    VertxContextSafetyToggle.setCurrentContextSafe(true);
                                    maintain().subscribe().with(
                                            changed -> LOG.debugf("Private audio maintenance transitions: %d", changed),
                                            failure -> LOG.warnf(
                                                    "Private audio maintenance did not complete (%s)",
                                                    failure.getClass().getSimpleName()));
                                }));
    }

    private <T> Uni<T> admitted(Function<Resources, Uni<T>> operation) {
        return Uni.createFrom().emitter(emitter -> {
            Optional.of(Context.isOnEventLoopThread())
                    .filter(Boolean::booleanValue)
                    .orElseThrow(() -> new IllegalStateException("Audio runtime requires a Vert.x event-loop"));
            accepted(operation).subscribe().with(emitter::complete, emitter::fail);
        });
    }

    private <T> Uni<T> accepted(Function<Resources, Uni<T>> operation) {
        return admission.tryAcquire()
                ? Uni.createFrom().deferred(
                        () -> stopping.get()
                                ? unavailable()
                                : resources.map(operation)
                                        .orElseGet(PrivateAudioRuntime::unavailable))
                        .eventually(() -> {
                            admission.release();
                        })
                : unavailable();
    }

    private static <T> Uni<T> unavailable() {
        return Uni.createFrom().failure(new AudioOperationConflictException());
    }

    @Override
    @SuppressWarnings("PMD.ForbiddenUnusedLambdaParameter") // LIFECYCLE-GUARD: 資源の内容ではなく所有者のclose完了状態で二重解放を防ぐ。
    public synchronized void close() throws Exception {
        stopping.set(true);
        vertx.cancelTimer(timer.getAndSet(-1));
        Optional.of(admission.tryAcquire())
                .filter(Boolean::booleanValue)
                .orElseThrow(() -> new IOException("Private audio operation is still active"));
        try {
            for (final var active : resources.filter(value -> Boolean.FALSE.equals(closed.get())).stream().toList()) {
                active.inputs.shutdownNow();
                active.temporary.close();
                active.storageOwner.close();
            }
            closed.set(true);
        } finally {
            admission.release();
        }
    }

    private record Resources(AudioTemporaryStore temporary, ThreadPoolExecutor inputs, PrivateAudioIngestion ingestion,
            PrivateAudioReconciliation reconciliation, Duration inputTimeout, Duration interval,
            AutoCloseable storageOwner) {
        private Resources {
            Objects.requireNonNull(temporary);
            Objects.requireNonNull(inputs);
            Objects.requireNonNull(ingestion);
            Objects.requireNonNull(reconciliation);
            Objects.requireNonNull(inputTimeout);
            Objects.requireNonNull(interval);
            Objects.requireNonNull(storageOwner);
        }
    }
}
