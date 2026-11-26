package com.abservice.application.audio;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Public projection of a pairing request; neither stored digest is exposed. */
public record ListeningAuthorization(String state,
        @Nullable UUID requestId,
        @Nullable String label,

        @Nullable Instant expiresAt,
        @Nullable UUID deviceId,
        int retryAfterSeconds) {
    public static ListeningAuthorization status(String state,
            int retryAfterSeconds) {
        return new ListeningAuthorization(state,
                null,
                null,
                null,
                null,
                retryAfterSeconds);
    }
}
