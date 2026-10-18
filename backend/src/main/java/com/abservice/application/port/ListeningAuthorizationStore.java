package com.abservice.application.port;

import com.abservice.application.audio.ListeningAuthorization;
import io.smallrye.mutiny.Uni;
import java.util.UUID;

/** Each operation commits before returning, including throttled attempts. */
public interface ListeningAuthorizationStore {
    Uni<ListeningAuthorization> start(UUID id,
            String label,
            String codeDigest,
            String proofDigest);

    Uni<ListeningAuthorization> resolve(String codeDigest);

    Uni<ListeningAuthorization> decide(UUID id,
            boolean approve,
            int validDays);

    /**
     * Consuming a request and inserting the listener credential are one
     * transaction.
     */
    Uni<ListeningAuthorization> poll(UUID id,
            String proofDigest,
            String tokenDigest);
}
