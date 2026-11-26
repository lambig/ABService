package com.abservice.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.config.SmallRyeConfigBuilder;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("音源入力期限の設定境界")
class PrivateAudioRuntimeConfigurationTest {
    @Test
    @DisplayName("期限を指定しない既存設定は2分を維持する")
    void retainsDefaultDeadline() {
        final var config = builder().build().getConfigMapping(PrivateAudioConfig.class);
        assertThat(config.inputTimeout()).isEqualTo(Duration.ofMinutes(2));
        PrivateAudioRuntimeProducer.validate(config);
    }

    @Test
    @DisplayName("明示した10分の期限を受け入れる")
    void acceptsBoundedExtendedDeadline() {
        final var config = configuration("PT600S");
        assertThat(config.inputTimeout()).isEqualTo(Duration.ofMinutes(10));
        PrivateAudioRuntimeProducer.validate(config);
    }

    @Test
    @DisplayName("ゼロ・負値・上限を超える入力期限は拒否する")
    void rejectsUnboundedDeadline() {
        for (final var value : new String[]{"PT0S", "PT-1S", "PT600.001S"}) {
            final var config = configuration(value);
            assertThatThrownBy(() -> PrivateAudioRuntimeProducer.validate(config))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static PrivateAudioConfig configuration(String duration) {
        return builder().withDefaultValue("abservice.private-audio.input-timeout", duration)
                .build().getConfigMapping(PrivateAudioConfig.class);
    }

    private static SmallRyeConfigBuilder builder() {
        return new SmallRyeConfigBuilder().withMapping(PrivateAudioConfig.class)
                .withDefaultValue("abservice.private-audio.temporary-directory", "/synthetic-audio");
    }
}
