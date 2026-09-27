package com.abservice.application.audio;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.PrivateAudioRegistration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** URL・保存キーを含まない管理用の登録状態。期限は新しい検査の受付期限。 */
public record AudioRegistrationView(UUID audioId, String state, Instant createdAt, Instant expiresAt,
        @Nullable FlacMetadata metadata) {
    public static AudioRegistrationView of(PrivateAudioRegistration registration) {
        return switch (registration.state()) {
            case PrivateAudioRegistration.Pending _ -> view(
                    registration,
                    "PENDING",
                    null);
            case PrivateAudioRegistration.Expired _ -> view(
                    registration,
                    "EXPIRED",
                    null);
            case PrivateAudioRegistration.Inspected inspected -> view(
                    registration,
                    "INSPECTED",
                    inspected.metadata());
            case PrivateAudioRegistration.Confirmed confirmed -> view(
                    registration,
                    "CONFIRMED",
                    confirmed.metadata());
            case PrivateAudioRegistration.Abandoned abandoned -> view(
                    registration,
                    "ABANDONED",
                    abandoned.metadata());
        };
    }

    private static AudioRegistrationView view(PrivateAudioRegistration registration, String state,
            @Nullable FlacMetadata metadata) {
        return new AudioRegistrationView(registration.id(), state, registration.createdAt(), registration.expiresAt(),
                metadata);
    }
}
