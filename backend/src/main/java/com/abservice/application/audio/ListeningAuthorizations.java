package com.abservice.application.audio;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.port.ListeningAuthorizationStore;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.EntityId;
import com.abservice.lib.ErrorResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Pairing secrets exist only during their issuing response; a short code never
 * redeems a token.
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION,
        Failure.NOT_FOUND})
public class ListeningAuthorizations {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final PrivateAudioAccess access;
    private final ListeningAuthorizationStore store;

    public Uni<Started> start(@Nullable String label) {
        return Uni.createFrom()
                .item(() -> {
                    access.requireEnabled();
                    return text(
                            label,
                            100);
                })
                .chain(valid -> {
                    final var code = IntStream.generate(() -> RANDOM.nextInt(ALPHABET.length()))
                            .limit(8)
                            .mapToObj(index -> String.valueOf(ALPHABET.charAt(index)))
                            .collect(Collectors.joining());
                    final byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    final var proof = HexFormat.of()
                            .formatHex(bytes);
                    return store.start(
                            UUID.fromString(EntityId.generateUuidV7()),

                            valid,

                            ListeningDeviceToken.digestOf(code),

                            ListeningDeviceToken.digestOf(proof))
                            .map(
                                    result -> new Started(result,

                                            "PENDING".equals(result.state())
                                                    ? code.substring(
                                                            0,
                                                            4) + "-" + code.substring(4)
                                                    : null,

                                            "PENDING".equals(result.state())
                                                    ? proof
                                                    : null));
                });
    }

    public Uni<ListeningAuthorization> resolve(@Nullable String code) {
        return Uni.createFrom()
                .item(() -> {
                    access.requireEnabled();
                    return text(
                            code,
                            32)
                            .replace(
                                    "-",
                                    "")
                            .replace(
                                    " ",
                                    "")
                            .toUpperCase(Locale.ROOT);
                })
                .chain(value -> store.resolve(ListeningDeviceToken.digestOf(value)));
    }

    public Uni<ListeningAuthorization> decide(String id,
            @Nullable Boolean approve,
            @Nullable Integer validDays) {
        return Uni.createFrom()
                .item(() -> {
                    access.requireEnabled();
                    return Optional.ofNullable(validDays)
                            .orElse(30);
                })
                .chain(
                        days -> store.decide(
                                id(id),

                                decision(approve),

                                Optional.of(days)
                                        .filter(value -> value >= 1)
                                        .filter(value -> value <= 90)
                                        .orElseThrow(ListeningAuthorizations::invalid)));
    }

    public Uni<Polled> poll(@Nullable String id,
            @Nullable String proof) {
        return Uni.createFrom()
                .item(() -> {
                    access.requireEnabled();
                    return Optional.of(
                            text(
                                    proof,
                                    64))
                            .filter(value -> value.matches("[0-9a-f]{64}"))
                            .orElseThrow(ListeningAuthorizations::invalid);
                })
                .chain(valid -> {
                    final var token = ListeningDeviceToken.issue();
                    return store.poll(
                            id(id),
                            ListeningDeviceToken.digestOf(valid),
                            token.digest())
                            .map(
                                    result -> new Polled(result,
                                            "ISSUED".equals(result.state())
                                                    ? token.value()
                                                    : null));
                });
    }

    private static boolean decision(@Nullable Boolean value) {
        return Optional.ofNullable(value)
                .orElseThrow(ListeningAuthorizations::invalid);
    }

    private static UUID id(@Nullable String value) {
        return UUID.fromString(
                Optional.of(
                        text(
                                value,
                                36))
                        .filter(
                                candidate -> candidate
                                        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                        .orElseThrow(ListeningAuthorizations::invalid));
    }

    private static String text(@Nullable String value,
            int maximum) {
        return Optional.ofNullable(value)
                .map(String::strip)
                .filter(candidate -> candidate.length() > 0)
                .filter(candidate -> candidate.length() <= maximum)
                .orElseThrow(ListeningAuthorizations::invalid);
    }

    private static ValidationException invalid() {
        return new ValidationException(
                List.of(
                        new ErrorResult("authorization",
                                "接続要求の入力を確認してください",
                                "INVALID_AUTHORIZATION")));
    }

    public record Started(ListeningAuthorization request,
            @Nullable String code,
            @Nullable String proof) {
        @Override
        public String toString() {
            return "Started[<redacted>]";
        }
    }

    public record Polled(ListeningAuthorization request,
            @Nullable String token) {
        @Override
        public String toString() {
            return "Polled[<redacted>]";
        }
    }
}
