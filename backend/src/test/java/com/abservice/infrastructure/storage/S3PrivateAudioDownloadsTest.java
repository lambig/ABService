package com.abservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.infrastructure.audio.PrivateAudioConfig;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 実SDKで署名し、ネットワークへ送らずに取得URLの宛先と期限の短縮を検証する。有効時間の計算は固定時計で行い、SDKの署名日時は
 * 実時刻になるため、期限は実時刻からの幅で見る。
 */
@DisplayName("非公開音源の取得URLの宛先と期限")
class S3PrivateAudioDownloadsTest {
    private static final UUID AUDIO = UUID.fromString("0192f8a0-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    @DisplayName("確定済み実体のキーを指し、端末の期限が遠ければ設定の有効時間で発行する")
    void signsVerifiedKeyWithConfiguredExpiry() {
        try (var signer = signer()) {
            final var before = Instant.now();
            final var download = downloads(signer, Duration.ofMinutes(10)).presign(AUDIO, NOW.plus(Duration.ofDays(1)));

            assertThat(URI.create(download.url()).getPath())
                    .isEqualTo("/example-private-audio/audio/verified/" + AUDIO + ".flac");
            assertThat(query(download.url()).get("X-Amz-Expires")).isEqualTo("600");
            assertThat(download.expiresAt()).isBetween(before.plusSeconds(599), before.plusSeconds(611));
        }
    }

    @Test
    @DisplayName("端末の資格情報の期限が近ければ、その残り時間まで短縮する")
    void capsExpiryAtTheDeviceCredential() {
        try (var signer = signer()) {
            final var before = Instant.now();
            final var download = downloads(signer, Duration.ofMinutes(10)).presign(AUDIO, NOW.plusSeconds(90));

            assertThat(query(download.url()).get("X-Amz-Expires")).isEqualTo("90");
            assertThat(download.expiresAt()).isBetween(before.plusSeconds(89), before.plusSeconds(101));
        }
    }

    @Test
    @DisplayName("端末の資格情報が既に期限切れならURLを発行しない")
    void refusesExpiredDeviceCredential() {
        try (var signer = signer()) {
            final var downloads = downloads(signer, Duration.ofMinutes(10));
            assertThatThrownBy(() -> downloads.presign(AUDIO, NOW)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("バケットが未設定なら発行しない")
    void requiresBucket() {
        try (var signer = signer()) {
            final var downloads = new S3PrivateAudioDownloads(
                    signer,
                    config(Optional.empty(), Duration.ofMinutes(10)),
                    Clock.fixed(NOW, ZoneOffset.UTC));
            assertThatThrownBy(() -> downloads.presign(AUDIO, NOW.plusSeconds(600)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("bucket");
        }
    }

    private static S3PrivateAudioDownloads downloads(S3UrlPresigner signer, Duration expiry) {
        return new S3PrivateAudioDownloads(
                signer,
                config(Optional.of("example-private-audio"), expiry),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static S3UrlPresigner signer() {
        return new S3UrlPresigner(
                S3Presigner.builder()
                        .credentialsProvider(() -> AwsBasicCredentials.create("invalid-static-key", "invalid-secret"))
                        .region(Region.US_EAST_1)
                        .endpointOverride(URI.create("https://storage.example.test"))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .build(),
                () -> AwsBasicCredentials.create("invalid-static-key", "invalid-secret"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Map<String, String> query(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(
                        Collectors.toUnmodifiableMap(
                                parts -> parts[0],
                                parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
    }

    /** 取得URLに関わる項目だけを持つ設定。他の項目は既定値で、本テストでは読まない。 */
    private static PrivateAudioConfig config(Optional<String> bucket, Duration expiry) {
        return new PrivateAudioConfig() {
            @Override
            public String enabled() {
                return "true";
            }

            @Override
            public Optional<String> bucket() {
                return bucket;
            }

            @Override
            public Optional<String> temporaryDirectory() {
                return Optional.empty();
            }

            @Override
            public long temporaryBytes() {
                return 536870912L;
            }

            @Override
            public Duration inputTimeout() {
                return Duration.ofMinutes(2);
            }

            @Override
            public Duration retention() {
                return Duration.ofHours(24);
            }

            @Override
            public Duration maintenanceInterval() {
                return Duration.ofMinutes(15);
            }

            @Override
            public Duration downloadUrlExpiry() {
                return expiry;
            }
        };
    }
}
