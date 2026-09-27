package com.abservice.infrastructure.audio;

import com.abservice.application.port.FlacInspectionLimits;
import com.abservice.application.port.AudioOperationConflictException;
import com.abservice.application.port.FlacInspector;
import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.port.PrivateAudioStorage;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 予約済み音源の検査・先行commit・保存照合・確定を接続する技術的な処理境界。CDI/APIへは接続しない。 DBは呼出元のVert.x
 * context、検査・保存・closeはworkerで実行する。外部I/O中にDBトランザクションを保持しない。
 * 購読の取消だけでは内部処理を中断せず、同時実行枠とsnapshotは処理の終了まで保持する。
 */
public final class PrivateAudioIngestion {
    private final PrivateAudioRegistrations registrations;
    private final FlacInspector inspector;
    private final VerifiedAudioPublication publication;
    private final Semaphore admission;

    public PrivateAudioIngestion(PrivateAudioRegistrations registrations, FlacInspector inspector,
            PrivateAudioStorage storage, int concurrency, int writeAttempts) {
        this.registrations = Objects.requireNonNull(registrations);
        this.inspector = Objects.requireNonNull(inspector);
        this.publication = new VerifiedAudioPublication(storage, writeAttempts);
        this.admission = new Semaphore(Optional.of(concurrency)
                .filter(count -> count > 0)
                .filter(count -> count <= 8)
                .orElseThrow(() -> new IllegalArgumentException("Invalid audio concurrency")));
    }

    /**
     * 内部発行・予約済みIDへ登録する。sourceは受入後にworkerで一度開き、検査終了時に閉じる。
     * sourceの読込期限は供給側が保証する。再購読時は新しい入力を供給し、確定済みIDの再登録は拒否する。
     */
    public Uni<FlacMetadata> ingest(
            UUID id,
            Callable<InputStream> source,
            FlacInspectionLimits limits) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(source);
        Objects.requireNonNull(limits);
        return admitted(
                context -> registrations.find(id).chain(
                        found -> found.filter(row -> row.state() instanceof PrivateAudioRegistration.Pending)
                                .isPresent()
                                        ? blocking(context, () -> inspect(source, limits)).chain(
                                                snapshot -> Uni.createFrom()
                                                        .deferred(
                                                                () -> recordAndPublish(
                                                                        context,
                                                                        id,
                                                                        snapshot))
                                                        .eventually(() -> blocking(context, () -> {
                                                            snapshot.close();
                                                            return true;
                                                        })))
                                        : conflict()));
    }

    /** 保存応答喪失・DB確定前の中断から復旧する。入力の再取得・再検査・再PUT・実体削除はしない。 */
    public Uni<FlacMetadata> recover(UUID id) {
        Objects.requireNonNull(id);
        return admitted(
                context -> registrations.find(id).chain(
                        found -> found
                                .map(
                                        row -> row.state() instanceof PrivateAudioRegistration.Inspected inspected
                                                ? blocking(
                                                        context,
                                                        () -> publication.findMatching(id, inspected.metadata()))
                                                        .chain(metadata -> confirm(id, metadata))
                                                : PrivateAudioIngestion.<FlacMetadata>conflict())
                                .orElseGet(PrivateAudioIngestion::conflict)));
    }

    private Uni<FlacMetadata> recordAndPublish(
            Context context,
            UUID id,
            InspectedAudio snapshot) {
        return registrations.recordInspection(id, snapshot.metadata()).chain(
                recorded -> recorded
                        ? blocking(context, () -> publication.publish(id, snapshot))
                                .chain(metadata -> confirm(id, metadata))
                        : conflict());
    }

    private Uni<FlacMetadata> confirm(UUID id, FlacMetadata metadata) {
        return registrations.confirm(id, metadata).chain(
                confirmed -> confirmed
                        ? Uni.createFrom().item(metadata)
                        : conflict());
    }

    private static <T> Uni<T> conflict() {
        return Uni.createFrom().failure(new AudioOperationConflictException());
    }

    private <T> Uni<T> admitted(Function<Context, Uni<T>> operation) {
        return Uni.createFrom().emitter(emitter -> {
            schedule(invocationContext(), operation).subscribe().with(emitter::complete, emitter::fail);
        });
    }

    private static Context invocationContext() {
        Optional.of(Context.isOnEventLoopThread())
                .filter(Boolean::booleanValue)
                .orElseThrow(() -> new IllegalStateException("Audio ingestion requires a Vert.x event-loop thread"));
        return Objects.requireNonNull(Vertx.currentContext());
    }

    private <T> Uni<T> schedule(Context context, Function<Context, Uni<T>> operation) {
        return admission.tryAcquire()
                ? Uni.createFrom().deferred(() -> operation.apply(context)).eventually(() -> {
                    admission.release();
                })
                : Uni.createFrom().failure(new IOException("Audio ingestion busy"));
    }

    @SuppressWarnings("PMD.SingleUseLocalVariable") // RESOURCE-LIFETIME: 入力は検査だけでなく暗黙のcloseまで所有する。
    private InspectedAudio inspect(Callable<InputStream> source, FlacInspectionLimits limits) throws Exception {
        final var inspected = new AtomicReference<InspectedAudio>();
        try (var input = source.call()) {
            final var snapshot = inspector.inspect(input, limits);
            inspected.set(snapshot);
            return snapshot;
        } catch (Exception failure) {
            Optional.ofNullable(inspected.get())
                    .ifPresent(snapshot -> discard(snapshot, failure));
            throw failure;
        }
    }

    private static void discard(InspectedAudio snapshot, Exception failure) {
        try {
            snapshot.close();
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private static <T> Uni<T> blocking(Context context, Callable<T> work) {
        return Uni.createFrom().completionStage(() -> context.executeBlocking(work, false).toCompletionStage());
    }
}
