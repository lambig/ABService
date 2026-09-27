package com.abservice.application.port;

import java.util.Objects;

/** 検査済みの実測値。totalSamplesはチャンネル当たりのサンプル数。 */
public record FlacMetadata(long byteLength, String sha256, int sampleRate, int channels,
        int bitsPerSample, long totalSamples) {
    public FlacMetadata {
        Objects.requireNonNull(sha256);
    }

    public long durationMillis() {
        return Math.ceilDiv(totalSamples * 1000, sampleRate);
    }
}
