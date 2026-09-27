package com.abservice.infrastructure.storage;

import com.abservice.application.port.FlacMetadata;
import java.io.IOException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/** 実体と同じPUTに載せる照合用メタデータ。外部のタグ・ファイル名は保存しない。 */
final class PrivateAudioObjectMetadata {
    private static final Set<Integer> CHANNELS = Set.of(1, 2);
    private static final Set<Integer> BIT_DEPTHS = Set.of(16, 24);
    private PrivateAudioObjectMetadata() {
    }

    static Map<String, String> encode(FlacMetadata metadata) throws IOException {
        validate(metadata);
        return Map.of(
                "audio-schema",
                "1",
                "sha256",
                metadata.sha256(),
                "sample-rate",
                Integer.toString(metadata.sampleRate()),
                "channels",
                Integer.toString(metadata.channels()),
                "bits-per-sample",
                Integer.toString(metadata.bitsPerSample()),
                "total-samples",
                Long.toString(metadata.totalSamples()));
    }

    static String checksum(FlacMetadata metadata) {
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(metadata.sha256()));
    }

    static FlacMetadata decode(HeadObjectResponse response) throws IOException {
        try {
            require("audio/flac".equals(response.contentType()));
            require("1".equals(response.metadata().get("audio-schema")));
            final var metadata = new FlacMetadata(
                    Objects.requireNonNull(response.contentLength()),
                    required(response, "sha256"),
                    Integer.parseInt(required(response, "sample-rate")),
                    Integer.parseInt(required(response, "channels")),
                    Integer.parseInt(required(response, "bits-per-sample")),
                    Long.parseLong(required(response, "total-samples")));
            validate(metadata);
            require(Objects.equals(checksum(metadata), response.checksumSHA256()));
            return metadata;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new IOException("Invalid private audio object metadata", failure);
        }
    }

    private static String required(HeadObjectResponse response, String name) {
        return Objects.requireNonNull(response.metadata().get(name));
    }

    private static void validate(FlacMetadata metadata) throws IOException {
        require(metadata.byteLength() > 0);
        require(metadata.sha256().matches("[0-9a-f]{64}"));
        require(metadata.sampleRate() >= 8000);
        require(metadata.sampleRate() <= 96000);
        require(CHANNELS.contains(metadata.channels()));
        require(BIT_DEPTHS.contains(metadata.bitsPerSample()));
        require(metadata.totalSamples() > 0);
        require(metadata.totalSamples() <= 0xfffffffffL);
    }

    private static void require(boolean valid) throws IOException {
        Optional.of(valid)
                .filter(Boolean::booleanValue)
                .orElseThrow(() -> new IOException("Invalid private audio object metadata"));
    }
}
