package com.abservice.infrastructure.audio;

import com.abservice.application.port.FlacMetadata;
import com.abservice.application.port.AudioOperationConflictException;
import com.abservice.application.port.InspectedAudio;
import com.abservice.application.port.PrivateAudioConflictException;
import com.abservice.application.port.PrivateAudioStorage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 同じ検査snapshotの条件付き保存と実測値の照合。未存在と確認できた場合だけ再PUTする。 */
final class VerifiedAudioPublication {
    private final PrivateAudioStorage storage;
    private final int attempts;

    VerifiedAudioPublication(PrivateAudioStorage storage, int attempts) {
        this.storage = Objects.requireNonNull(storage);
        this.attempts = Optional.of(attempts)
                .filter(count -> count > 0)
                .filter(count -> count <= 3)
                .orElseThrow(() -> new IllegalArgumentException("Audio write attempts must be between 1 and 3"));
    }

    FlacMetadata publish(UUID id, InspectedAudio snapshot) throws IOException {
        return publish(
                id,
                snapshot,
                attempts);
    }

    FlacMetadata findMatching(UUID id, FlacMetadata expected) throws IOException {
        return matching(
                storage.find(id)
                        .orElseThrow(() -> new IOException("Stored audio is absent")),
                expected);
    }

    private FlacMetadata publish(
            UUID id,
            InspectedAudio snapshot,
            int remaining) throws IOException {
        final var existing = storage.find(id);
        return existing.isPresent()
                ? matching(existing.orElseThrow(), snapshot.metadata())
                : writeAndVerify(
                        id,
                        snapshot,
                        remaining);
    }

    private FlacMetadata writeAndVerify(
            UUID id,
            InspectedAudio snapshot,
            int remaining) throws IOException {
        return verifyWritten(
                id,
                snapshot,
                remaining,
                write(id, snapshot));
    }

    private FlacMetadata verifyWritten(
            UUID id,
            InspectedAudio snapshot,
            int remaining,
            Optional<IOException> failure) throws IOException {
        final var stored = storage.find(id);
        return stored.isPresent()
                ? matching(stored.orElseThrow(), snapshot.metadata())
                : retry(
                        id,
                        snapshot,
                        remaining,
                        failure.orElseGet(() -> new IOException("Stored audio is absent")));
    }

    private Optional<IOException> write(UUID id, InspectedAudio snapshot) {
        try {
            storage.write(id, snapshot);
            return Optional.empty();
        } catch (IOException failure) {
            return Optional.of(failure);
        }
    }

    private FlacMetadata retry(
            UUID id,
            InspectedAudio snapshot,
            int remaining,
            IOException failure)
            throws IOException {
        Optional.of(Thread.currentThread().isInterrupted())
                .filter(Boolean.FALSE::equals)
                .orElseThrow(() -> failure);
        return switch (failure) {
            case PrivateAudioConflictException conflict -> throw conflict;
            case InterruptedIOException interrupted -> throw interrupted;
            default -> publish(
                    id,
                    snapshot,
                    Optional.of(remaining)
                            .filter(count -> count > 1)
                            .orElseThrow(() -> failure) - 1);
        };
    }

    private static FlacMetadata matching(FlacMetadata actual, FlacMetadata expected) throws IOException {
        return Optional.of(actual)
                .filter(expected::equals)
                .orElseThrow(AudioOperationConflictException::new);
    }
}
