package com.abservice.presentation.rest.audio.response;

import com.abservice.application.audio.ListeningAuthorization;
import com.abservice.application.audio.ListeningAuthorizations;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Secrets occur only on start/first successful redemption, with no-store
 * headers.
 */
public record ListeningAuthorizationResponse(String state,
        @Nullable UUID requestId,
        @Nullable String label,

        @Nullable Instant expiresAt,
        @Nullable UUID deviceId,
        int retryAfterSeconds,

        @Nullable String code,
        @Nullable String proof,
        @Nullable String token) {
    public static ListeningAuthorizationResponse of(ListeningAuthorization value) {
        return new ListeningAuthorizationResponse(value.state(),
                value.requestId(),
                value.label(),
                value.expiresAt(),

                value.deviceId(),
                value.retryAfterSeconds(),
                null,
                null,
                null);
    }

    public static ListeningAuthorizationResponse started(ListeningAuthorizations.Started value) {
        final var request = value.request();
        return new ListeningAuthorizationResponse(request.state(),
                request.requestId(),
                request.label(),

                request.expiresAt(),

                request.deviceId(),
                request.retryAfterSeconds(),
                value.code(),
                value.proof(),
                null);
    }

    public static ListeningAuthorizationResponse polled(ListeningAuthorizations.Polled value) {
        final var request = value.request();
        return new ListeningAuthorizationResponse(request.state(),
                request.requestId(),
                request.label(),

                request.expiresAt(),

                request.deviceId(),
                request.retryAfterSeconds(),
                null,
                null,
                value.token());
    }

    @Override
    public String toString() {
        return "ListeningAuthorizationResponse[<redacted>]";
    }
}
