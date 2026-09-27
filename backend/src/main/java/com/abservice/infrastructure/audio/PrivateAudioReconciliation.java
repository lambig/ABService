package com.abservice.infrastructure.audio;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.PrivateAudioMaintenance;
import com.abservice.application.port.PrivateAudioRegistration;
import com.abservice.application.port.PrivateAudioRegistrations;
import com.abservice.application.port.PrivateAudioStorage;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** HEAD照合を伴う復旧と有界の期限処理。実体は削除せず、判断不能ならDB状態も維持する。 */
final class PrivateAudioReconciliation {
    private final PrivateAudioRegistrations registrations;
    private final PrivateAudioMaintenance maintenance;
    private final PrivateAudioStorage storage;
    private final Duration retention;

    PrivateAudioReconciliation(PrivateAudioRegistrations registrations, PrivateAudioMaintenance maintenance,
            PrivateAudioStorage storage, Duration retention) {
        this.registrations = Objects.requireNonNull(registrations);
        this.maintenance = Objects.requireNonNull(maintenance);
        this.storage = Objects.requireNonNull(storage);
        this.retention = Objects.requireNonNull(retention);
    }

    Uni<FlacMetadata> recover(UUID id) {
        return registrations.find(id).chain(
                found -> found.map(row -> expected(row.state()))
                        .map(
                                expected -> head(id).chain(
                                        stored -> stored.filter(expected::equals)
                                                .map(
                                                        metadata -> maintenance.confirmRecovered(id, metadata)
                                                                .chain(
                                                                        confirmed -> confirmed
                                                                                ? Uni.createFrom().item(metadata)
                                                                                : conflict()))
                                                .orElseGet(PrivateAudioReconciliation::conflict)))
                        .orElseGet(PrivateAudioReconciliation::conflict));
    }

    Uni<Integer> maintain(int batchSize) {
        return registrations.expirePending(batchSize).chain(
                expired -> maintenance.staleInspected(retention, batchSize)
                        .chain(this::reconcileBatch).map(changed -> expired.size() + changed));
    }

    private Uni<Integer> reconcileBatch(List<UUID> ids) {
        return Multi.createFrom().iterable(ids).onItem().transformToUniAndConcatenate(this::reconcile)
                .collect().asList().map(changed -> (int) changed.stream().filter(Boolean::booleanValue).count());
    }

    private Uni<Boolean> reconcile(UUID id) {
        return registrations.find(id).chain(
                found -> found
                        .filter(row -> row.state() instanceof PrivateAudioRegistration.Inspected)
                        .map(
                                row -> head(id).chain(
                                        stored -> stored
                                                .map(
                                                        actual -> actual.equals(expected(row.state()))
                                                                ? registrations.confirm(id, actual)
                                                                : PrivateAudioReconciliation.<Boolean>conflict())
                                                .orElseGet(() -> maintenance.abandonInspected(id, retention))))
                        .orElseGet(() -> Uni.createFrom().item(false)));
    }

    private Uni<Optional<FlacMetadata>> head(UUID id) {
        return Uni.createFrom().completionStage(
                () -> Objects.requireNonNull(Vertx.currentContext())
                        .executeBlocking(() -> storage.find(id), false).toCompletionStage());
    }

    private static FlacMetadata expected(PrivateAudioRegistration.State state) {
        return switch (state) {
            case PrivateAudioRegistration.Inspected inspected -> inspected.metadata();
            case PrivateAudioRegistration.Abandoned abandoned -> abandoned.metadata();
            default -> throw new IllegalStateException("Audio registration is not recoverable");
        };
    }

    private static <T> Uni<T> conflict() {
        return Uni.createFrom().failure(new IOException("Audio recovery needs matching stored metadata"));
    }
}
