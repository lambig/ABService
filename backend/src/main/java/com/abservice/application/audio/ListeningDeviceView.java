package com.abservice.application.audio;

import com.abservice.application.port.ListeningDevice;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** トークンを含まない端末資格情報の表現。状態は観測時刻で決まり、DB時計の判定を代替しない。 */
public record ListeningDeviceView(UUID deviceId, String label, String state, Instant createdAt, Instant expiresAt,
        @Nullable Instant revokedAt) {
    public static ListeningDeviceView of(ListeningDevice device, Instant now) {
        return new ListeningDeviceView(device.id(), device.label(), state(device, now), device.createdAt(),
                device.expiresAt(), device.revokedAt());
    }

    private static String state(ListeningDevice device, Instant now) {
        return Optional.ofNullable(device.revokedAt())
                .map(revoked -> "REVOKED")
                .orElseGet(
                        () -> device.expiresAt().isAfter(now)
                                ? "ACTIVE"
                                : "EXPIRED");
    }
}
