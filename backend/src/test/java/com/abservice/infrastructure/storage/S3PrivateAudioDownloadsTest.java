package com.abservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

/** 実SDKで署名し、ネットワークへ送らずに取得URLの宛先と、端末の期限を絶対の上限として渡すことを検証する。 */
@DisplayName("非公開音源の取得URLの宛先と期限")
class S3PrivateAudioDownloadsTest {
    private static final UUID AUDIO = UUID.fromString("0192f8a0-0000-7000-8000-000000000001");

    @Test
    @DisplayName("確定済み実体のキーを指し、端末の期限が遠ければ設定の有効時間で発行する")
    void signsVerifiedKeyWithConfiguredExpiry() throws Exception {
        final var before = Instant.now();
        try (var downloads = downloads(Duration.ofMinutes(10))) {
            final var download = downloads.presign(AUDIO, before.plus(Duration.ofDays(1)));

            assertThat(URI.create(download.url()).getPath())
                    .isEqualTo("/example-private-audio/audio/verified/" + AUDIO + ".flac");
            assertThat(query(download.url()).get("X-Amz-Expires")).isEqualTo("600");
            assertThat(query(download.url()).get("response-content-type")).isEqualTo("audio/flac");
            assertThat(download.expiresAt()).isBetween(before.plusSeconds(599), before.plusSeconds(611));
        }
    }

    @Test
    @DisplayName("端末の資格情報の期限が近ければ、その時刻を超えない")
    void capsExpiryAtTheDeviceCredential() throws Exception {
        final var notAfter = Instant.now().plusSeconds(90);
        try (var downloads = downloads(Duration.ofMinutes(10))) {
            final var download = downloads.presign(AUDIO, notAfter);

            assertThat(Long.parseLong(query(download.url()).get("X-Amz-Expires"))).isBetween(85L, 90L);
            assertThat(download.expiresAt()).isBeforeOrEqualTo(notAfter);
        }
    }

    @Test
    @DisplayName("端末の資格情報が既に期限切れならURLを発行しない")
    void refusesExpiredDeviceCredential() throws Exception {
        try (var downloads = downloads(Duration.ofMinutes(10))) {
            assertThatThrownBy(() -> downloads.presign(AUDIO, Instant.now().minusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("終了時に署名器と資格情報を閉じる")
    void closesOwnedResources() throws Exception {
        final var closed = new AtomicBoolean();
        final var downloads = new S3PrivateAudioDownloads(
                signer(),
                () -> closed.set(true),
                "example-private-audio",
                Duration.ofMinutes(10));
        downloads.close();
        assertThat(closed).isTrue();
    }

    private static S3PrivateAudioDownloads downloads(Duration expiry) {
        return new S3PrivateAudioDownloads(
                signer(),
                () -> {
                },
                "example-private-audio",
                expiry);
    }

    private static S3UrlPresigner signer() {
        return S3UrlPresigner.configured(
                () -> AwsBasicCredentials.create("invalid-static-key", "invalid-secret"),
                "us-east-1",
                true,
                Optional.of("https://storage.example.test"));
    }

    private static Map<String, String> query(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(
                        Collectors.toUnmodifiableMap(
                                parts -> parts[0],
                                parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
    }
}
