package com.abservice.presentation.rest.audio.response;

import com.abservice.application.audio.ListeningDeviceView;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** トークンを含まない端末資格情報。状態は ACTIVE / EXPIRED / REVOKED。 */
public record ListeningDeviceResponse(UUID deviceId, String label, String state, Instant createdAt,
        Instant expiresAt, @Nullable Instant revokedAt) {
    public static ListeningDeviceResponse of(ListeningDeviceView view) {
        return new ListeningDeviceResponse(view.deviceId(), view.label(), view.state(), view.createdAt(),
                view.expiresAt(), view.revokedAt());
    }
}
