package com.abservice.presentation.rest.audio.response;

import com.abservice.application.audio.AudioRegistrationView;
import com.abservice.application.port.FlacMetadata;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 音源実体のURL・保存先を公開しない管理用の登録状態。 */
public record AudioRegistrationResponse(UUID audioId, String state, Instant createdAt, Instant expiresAt,
        @Nullable AudioMetadataResponse metadata) {
    public static AudioRegistrationResponse of(AudioRegistrationView view) {
        return new AudioRegistrationResponse(view.audioId(), view.state(), view.createdAt(), view.expiresAt(),
                Optional.ofNullable(view.metadata())
                        .map(AudioMetadataResponse::of)
                        .orElse(null));
    }

    public record AudioMetadataResponse(long byteLength, String sha256, int sampleRate, int channels,
            int bitsPerSample, long totalSamples) {
        private static AudioMetadataResponse of(FlacMetadata metadata) {
            return new AudioMetadataResponse(metadata.byteLength(), metadata.sha256(), metadata.sampleRate(),
                    metadata.channels(), metadata.bitsPerSample(), metadata.totalSamples());
        }
    }
}
