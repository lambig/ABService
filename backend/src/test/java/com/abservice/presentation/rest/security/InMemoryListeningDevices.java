package com.abservice.presentation.rest.security;

import com.abservice.application.port.ListeningDevice;
import com.abservice.application.port.ListeningDevices;
import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** DBを使わず、与えたdigestの端末だけを有効として答える単体テスト用の資格情報。 */
final class InMemoryListeningDevices implements ListeningDevices {
    private final Map<String, ListeningDevice> active;

    InMemoryListeningDevices(Map<String, ListeningDevice> active) {
        this.active = Map.copyOf(active);
    }

    @Override
    public Uni<Boolean> create(
            UUID id,
            String label,
            String tokenDigest,
            Instant expiresAt) {
        return Uni.createFrom().item(Boolean.FALSE);
    }

    @Override
    public Uni<Optional<ListeningDevice>> find(UUID id) {
        return Uni.createFrom().item(Optional.empty());
    }

    @Override
    public Uni<List<ListeningDevice>> list() {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<Boolean> revoke(UUID id) {
        return Uni.createFrom().item(Boolean.FALSE);
    }

    @Override
    public Uni<Optional<ListeningDevice>> findActiveByDigest(String tokenDigest) {
        return Uni.createFrom().item(Optional.ofNullable(active.get(tokenDigest)));
    }
}
