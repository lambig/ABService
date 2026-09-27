package com.abservice.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FLAC検査上限の設定")
class FlacInspectionLimitsTest {
    @Test
    @DisplayName("画像と独立した音声のサイズ・時間・検査時間の既定値を持つ")
    void providesAudioLimits() {
        assertThat(FlacInspectionLimits.defaults().maxBytes()).isEqualTo(268435456L);
        assertThat(FlacInspectionLimits.defaults().maxDurationSeconds()).isEqualTo(7200);
        assertThat(FlacInspectionLimits.defaults().decodeTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("無制限・非正数・ミリ秒未満のタイムアウトを許可しない")
    void rejectsUnboundedLimits() {
        assertThatThrownBy(
                () -> new FlacInspectionLimits(
                        0,
                        1,
                        Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> new FlacInspectionLimits(
                        1,
                        -1,
                        Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> new FlacInspectionLimits(
                        1,
                        1,
                        Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
