package com.abservice.application.port;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** 音声専用の資源上限。端末容量や画像アップロード上限とは独立する。 */
public record FlacInspectionLimits(long maxBytes, long maxDurationSeconds, Duration decodeTimeout) {
    public FlacInspectionLimits {
        Objects.requireNonNull(decodeTimeout);
        requirePositive(maxBytes);
        requirePositive(maxDurationSeconds);
        requirePositive(decodeTimeout.toMillis());
    }

    /** 初期の受入上限。平均ファイルサイズから導いた値ではない。 */
    public static FlacInspectionLimits defaults() {
        return new FlacInspectionLimits(
                256L * 1024 * 1024,
                7200,
                Duration.ofMinutes(2));
    }

    private static void requirePositive(long value) {
        Optional.of(value)
                .filter(number -> number > 0)
                .orElseThrow(() -> new IllegalArgumentException("Audio inspection limits must be positive"));
    }
}
