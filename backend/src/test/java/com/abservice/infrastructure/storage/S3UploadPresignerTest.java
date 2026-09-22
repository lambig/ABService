package com.abservice.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abservice.application.port.PresignedUpload;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.regions.Region;

/** 実際のSDKで署名し、ネットワークへ送らずにURLの期限・資格情報・要求を検証する。 */
@DisplayName("S3アップロードURLの資格情報と期限")
class S3UploadPresignerTest {

    private static final Duration REQUESTED = Duration.ofMinutes(10);
    private static final PutObjectRequest REQUEST = PutObjectRequest.builder()
            .bucket("test-assets")
            .key("pending/test.png")
            .contentType("image/png")
            .build();
    private static final DateTimeFormatter SIGNING_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    @Test
    @DisplayName("残り5分の資格情報では10分のURLを返さない")
    void capsUrlAndReportedExpiryAtCredentialLifetime() {
        final var expiration = Instant.now().plusSeconds(300);
        try (var signer = signer(() -> session("invalid-first-key", expiration), Clock.systemUTC())) {
            final var upload = signer.presign(REQUEST, REQUESTED);
            final var query = query(upload);

            assertThat(Long.parseLong(query.get("X-Amz-Expires"))).isBetween(285L, 295L);
            assertThat(upload.expiresAt()).isBeforeOrEqualTo(expiration.minusSeconds(5));
            assertThat(upload.expiresAt()).isEqualTo(wireExpiry(query));
            assertThat(query.get("X-Amz-Credential")).startsWith("invalid-first-key/");
            assertThat(query.get("X-Amz-Security-Token")).isEqualTo("invalid-session-token");
            assertThat(query.get("X-Amz-SignedHeaders")).contains("content-type");
            assertThat(URI.create(upload.url()).getPath()).isEqualTo("/test-assets/pending/test.png");
        }
    }

    @Test
    @DisplayName("静的資格情報では設定した10分を維持し、小数秒を余分に表示しない")
    void retainsConfiguredDurationForStaticCredentials() {
        try (var signer = signer(
                () -> AwsBasicCredentials.create("invalid-static-key", "invalid-secret"),
                Clock.systemUTC())) {
            final var upload = signer.presign(REQUEST, REQUESTED);

            assertThat(query(upload).get("X-Amz-Expires")).isEqualTo("600");
            assertThat(upload.expiresAt()).isEqualTo(wireExpiry(query(upload)));
        }
    }

    @Test
    @DisplayName("次の発行では更新後の資格情報を使い、一回の発行中は再解決しない")
    void usesRefreshedCredentialsOnNextIssuance() {
        final var current = new AtomicReference<AwsCredentials>(
                session("invalid-old-key", Instant.now().plusSeconds(120)));
        final var calls = new AtomicInteger();
        try (var signer = signer(() -> countedCredentials(current, calls), Clock.systemUTC())) {
            final var first = signer.presign(REQUEST, REQUESTED);
            current.set(session("invalid-new-key", Instant.now().plusSeconds(900)));
            final var second = signer.presign(REQUEST, REQUESTED);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(query(first).get("X-Amz-Credential")).startsWith("invalid-old-key/");
            assertThat(query(second).get("X-Amz-Credential")).startsWith("invalid-new-key/");
            assertThat(query(second).get("X-Amz-Expires")).isEqualTo("600");
            assertThat(second.expiresAt()).isAfter(first.expiresAt());
        }
    }

    @Test
    @DisplayName("失効済み・余裕時間内・秒未満しか残らない資格情報でURLを返さない")
    void rejectsCredentialsWithoutUsableLifetime() {
        Stream.of(
                -1L,
                0L,
                4L,
                5L).forEach(S3UploadPresignerTest::assertRejectedLifetime);
    }

    @Test
    @DisplayName("期限情報のない一時資格情報で有効期限を約束しない")
    void rejectsSessionCredentialsWithoutExpiration() {
        try (var signer = signer(
                () -> AwsSessionCredentials.create(
                        "invalid-key",
                        "invalid-secret",
                        "invalid-token"),
                Clock.systemUTC())) {
            assertThatThrownBy(() -> signer.presign(REQUEST, REQUESTED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must include their expiration");
        }
    }

    @Test
    @DisplayName("失効済みの資格情報で失敗しても、更新後は同じ署名器で再発行できる")
    void recoversAfterExpiredCredentialsAreReplaced() {
        final var current = new AtomicReference<AwsCredentials>(
                session("invalid-expired-key", Instant.now().minusSeconds(1)));
        try (var signer = signer(current::get, Clock.systemUTC())) {
            assertThatThrownBy(() -> signer.presign(REQUEST, REQUESTED))
                    .isInstanceOf(IllegalStateException.class);
            current.set(session("invalid-recovered-key", Instant.now().plusSeconds(900)));

            final var recovered = signer.presign(REQUEST, REQUESTED);
            assertThat(query(recovered).get("X-Amz-Credential")).startsWith("invalid-recovered-key/");
            assertThat(query(recovered).get("X-Amz-Expires")).isEqualTo("600");
        }
    }

    @Test
    @DisplayName("資格情報を取得できないときは古いURLへフォールバックしない")
    void propagatesCredentialProviderFailure() {
        try (var signer = signer(S3UploadPresignerTest::unavailableCredentials, Clock.systemUTC())) {
            assertThatThrownBy(() -> signer.presign(REQUEST, REQUESTED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("credential provider unavailable");
        }
    }

    @Test
    @DisplayName("署名処理中に期限を過ぎた場合もURLを返さない")
    void checksValidityAfterSigning() {
        final var now = Instant.now();
        try (var signer = signer(
                () -> session("invalid-key", now.plusSeconds(60)),
                new AdvancingClock(now, now.plusSeconds(61)))) {
            assertThatThrownBy(() -> signer.presign(REQUEST, REQUESTED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expired while signing");
        }
    }

    private static void assertRejectedLifetime(long seconds) {
        final var now = Instant.now();
        try (var signer = signer(
                () -> session("invalid-key", now.plusSeconds(seconds).plusMillis(500)),
                Clock.fixed(now, ZoneOffset.UTC))) {
            assertThatThrownBy(() -> signer.presign(REQUEST, REQUESTED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no remaining validity");
        }
    }

    private static AwsCredentials countedCredentials(
            AtomicReference<AwsCredentials> current, AtomicInteger calls) {
        calls.incrementAndGet();
        return current.get();
    }

    private static AwsCredentials unavailableCredentials() {
        throw new IllegalStateException("credential provider unavailable");
    }

    private static AwsCredentials forbiddenDefaultCredentials() {
        throw new AssertionError("The signer must use the credentials resolved for this request");
    }

    private static AwsSessionCredentials session(String accessKey, Instant expiration) {
        return AwsSessionCredentials.builder()
                .accessKeyId(accessKey)
                .secretAccessKey("invalid-secret")
                .sessionToken("invalid-session-token")
                .expirationTime(expiration)
                .build();
    }

    private static S3UploadPresigner signer(AwsCredentialsProvider provider, Clock clock) {
        return new S3UploadPresigner(
                S3Presigner.builder()
                        .credentialsProvider(S3UploadPresignerTest::forbiddenDefaultCredentials)
                        .region(Region.US_EAST_1)
                        .endpointOverride(URI.create("https://storage.example.test"))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .build(),
                provider,
                clock);
    }

    private static Map<String, String> query(PresignedUpload upload) {
        return Arrays.stream(URI.create(upload.url()).getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(
                        Collectors.toUnmodifiableMap(
                                parts -> parts[0],
                                parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
    }

    private static Instant wireExpiry(Map<String, String> query) {
        return Instant.from(SIGNING_DATE.parse(query.get("X-Amz-Date")))
                .plusSeconds(Long.parseLong(query.get("X-Amz-Expires")));
    }

    private static final class AdvancingClock extends Clock {
        private final Instant beforeSigning;
        private final Instant afterSigning;
        private final AtomicInteger reads = new AtomicInteger();

        private AdvancingClock(Instant beforeSigning, Instant afterSigning) {
            this.beforeSigning = beforeSigning;
            this.afterSigning = afterSigning;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return reads.getAndIncrement() == 0
                    ? beforeSigning
                    : afterSigning;
        }
    }
}
