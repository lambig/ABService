package com.abservice.infrastructure.audio;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/** 実環境の値は運用側から与える。enabledが厳密にtrueの場合だけ実体を構築する。 */
@ConfigMapping(prefix = "abservice.private-audio")
public interface PrivateAudioConfig {
    @WithDefault("false")
    String enabled();
    Optional<String> bucket();
    Optional<String> temporaryDirectory();
    @WithDefault("536870912")
    long temporaryBytes();
    @WithDefault("PT2M")
    Duration inputTimeout();
    @WithDefault("PT24H")
    Duration retention();
    @WithDefault("PT15M")
    Duration maintenanceInterval();

    /** 端末へ発行する取得URLの最大有効時間。署名資格情報と端末の資格情報の残存時間で短縮される。 */
    @WithDefault("PT10M")
    Duration downloadUrlExpiry();
}
