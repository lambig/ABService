package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 実SDKで署名した取得URLを、専用MinIOバケットへ実際に投げて音源の同一性まで見る。
 *
 * <p>
 * 公開画像用の静的資格情報（{@code quarkus.s3.aws.credentials.*}）を無効な値にし、private audio
 * 側（AWS標準の provider chain）だけが正しい資格情報を持つ構成で走らせる。取得URLが公開画像用の署名器で署名されていれば MinIO
 * が拒むので、 保存側と同じ資格情報で署名していることが取得の成功で固定される。
 * </p>
 */
@QuarkusTest
@TestProfile(ListeningAssetUrlRestIntegrationTest.AudioRuntime.class)
@QuarkusTestResource(value = AudioHttpTestResource.class, restrictToAnnotatedClass = true)
@ExtendWith(CleanDatabase.class)
@DisplayName("端末向け音源取得URLのHTTP契約")
class ListeningAssetUrlRestIntegrationTest {
    /** 専用リソースでの再起動を通常テストと分離し、公開画像用の静的資格情報を private audio と別の無効な値にする。 */
    public static class AudioRuntime implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.s3.aws.credentials.static-provider.access-key-id",
                    "invalid-shared-key",
                    "quarkus.s3.aws.credentials.static-provider.secret-access-key",
                    "invalid-shared-secret");
        }
    }

    private static final String PACKAGE = "/api/v1/listening/package";
    private static final String DEVICES = "/api/v1/admin/listening-devices";
    private static final String REGISTRATIONS = "/api/v1/admin/private-audio/registrations";
    private static final String PROBLEM = "application/problem+json";
    /** 検査所有の実体を置くための MinIO クライアント。アプリの共有クライアントは無効な資格情報を持つため使わない。 */
    private final S3Client storage = S3Client.builder().endpointOverride(URI.create("http://localhost:9000"))
            .forcePathStyle(true).region(Region.US_EAST_1)
            .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create("minioadmin", "minioadmin123")))
            .build();

    @Test
    @DisplayName("現在のパッケージに含まれる音源だけURLを解決し、そのURLで取得した実体はSHA-256が一致する")
    void resolvesUrlsForDistributedAudioOnly() throws Exception {
        final String token = device().path("token");
        final String album = album("配布作品");
        final String audio = confirmed();
        final String unassigned = confirmed();
        final String draft = album("下書き作品");
        final String draftAudio = confirmed();
        assign(
                album,
                audio,
                0);
        assign(
                draft,
                draftAudio,
                0);
        publish(album);
        asDevice(token).get(PACKAGE).then().statusCode(200).body("assets", hasSize(1))
                .body("assets[0].assetId", equalTo(audio));

        final var before = Instant.now();
        final var response = asDevice(token).get(url(audio)).then().statusCode(200).contentType(ContentType.JSON)
                .header("Cache-Control", "no-store").body("assetId", equalTo(audio)).extract();
        final String url = response.path("url");
        final var expiresAt = Instant.parse(response.path("expiresAt"));
        assertThat(url).startsWith("http://localhost:9000/" + bucket() + "/audio/verified/" + audio + ".flac?");
        assertThat(query(url).get("X-Amz-Expires")).isEqualTo("600");
        assertThat(expiresAt).isBetween(before.plusSeconds(590), before.plusSeconds(611));
        assertThat(response.asString()).doesNotContain("storageKey").doesNotContain("minioadmin123");
        final String sha256 = authorized().get(REGISTRATIONS + "/" + audio).then().statusCode(200).extract()
                .path("metadata.sha256");
        assertThat(sha256Of(fetch(url))).isEqualTo(sha256);
        given().get("http://localhost:9000/" + bucket() + "/audio/verified/" + audio + ".flac").then()
                .statusCode(403);

        asDevice(token).get(url(unassigned)).then().statusCode(404).contentType(PROBLEM);
        asDevice(token).get(url(draftAudio)).then().statusCode(404).contentType(PROBLEM);
        asDevice(token).get(url(UUID.randomUUID().toString())).then().statusCode(404).contentType(PROBLEM);
        asDevice(token).get(url("not-a-uuid")).then().statusCode(400).contentType(PROBLEM);
        authorized().get(url(audio)).then().statusCode(403).contentType(PROBLEM);
        given().get(url(audio)).then().statusCode(401).contentType(PROBLEM);
    }

    @Test
    @DisplayName("関連付けの差し替えと非公開化で解決は止まるが、発行済みのURLは期限まで使える")
    void issuedUrlsOutliveReassignment() throws Exception {
        final String token = device().path("token");
        final String album = album("差し替え作品");
        final String first = confirmed();
        final String second = confirmed();
        assign(
                album,
                first,
                0);
        publish(album);
        final String issued = asDevice(token).get(url(first)).then().statusCode(200).extract().path("url");
        assign(
                album,
                second,
                1);
        asDevice(token).get(url(first)).then().statusCode(404);
        asDevice(token).get(url(second)).then().statusCode(200);
        assertThat(fetch(issued)).isNotEmpty();
        authorized().post("/api/v1/albums/" + album + "/unpublish").then().statusCode(200);
        asDevice(token).get(url(second)).then().statusCode(404);
        assertThat(fetch(issued)).isNotEmpty();
    }

    @Test
    @DisplayName("URLの期限は端末の資格情報の期限を超えない")
    void capsExpiryAtDeviceCredential() throws Exception {
        final var device = device();
        final String token = device.path("token");
        final String album = album("期限作品");
        final String audio = confirmed();
        assign(
                album,
                audio,
                0);
        publish(album);
        expireSoon(device.path("device.deviceId"));

        final var before = Instant.now();
        final var response = asDevice(token).get(url(audio)).then().statusCode(200).extract();
        final long expires = Long.parseLong(query(response.path("url")).get("X-Amz-Expires"));
        assertThat(expires).isBetween(150L, 180L);
        assertThat(Instant.parse(response.path("expiresAt"))).isBeforeOrEqualTo(before.plusSeconds(181));
        assertThat(fetch(response.path("url"))).isNotEmpty();
    }

    private static String url(String assetId) {
        return PACKAGE + "/assets/" + assetId + "/url";
    }

    private static RequestSpecification asDevice(String token) {
        return given().header("Authorization", "Bearer " + token);
    }

    private static ExtractableResponse<Response> device() {
        return authorized().contentType(ContentType.JSON).body(Map.of("label", "検査端末")).post(DEVICES).then()
                .statusCode(201).extract();
    }

    private static String album(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "title",
                                title,
                                "releaseDate",
                                "2026-01-01",
                                "artistDisplayName",
                                "Fixture"))
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static void publish(String album) {
        authorized().post("/api/v1/albums/" + album + "/publish").then().statusCode(200);
    }

    private static void assign(
            String album,
            String audio,
            int revision) {
        authorized().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "audioId",
                                audio,
                                "expectedRevision",
                                revision))
                .put("/api/v1/admin/albums/" + album + "/listening-audio/crossfade").then().statusCode(200);
    }

    /**
     * 検査器の代役として合成バイト列を確定済みキーへ直接置き、DBの登録を確定にする。FLACの検査は受信の統合試験が担い、
     * 本試験は確定後の取得URLと実体の同一性を検査する。
     */
    private String confirmed() throws Exception {
        final String id = authorized().post(REGISTRATIONS).then().statusCode(201).extract().path("audioId");
        final var bytes = new byte[8192];
        new Random(id.hashCode()).nextBytes(bytes);
        storage.putObject(
                request -> request.bucket(bucket()).key("audio/verified/" + id + ".flac").contentType("audio/flac"),
                RequestBody.fromBytes(bytes));
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.prepareStatement("""
                        UPDATE private_audio_registration SET state = 'CONFIRMED', byte_length = ?,
                            sha256 = ?, sample_rate = 44100, channels = 2, bits_per_sample = 16, total_samples = 44100
                        WHERE audio_id = ?
                        """)) {
            statement.setInt(1, bytes.length);
            statement.setString(2, sha256Of(bytes));
            statement.setObject(3, UUID.fromString(id));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        return id;
    }

    private static byte[] fetch(String url) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            final var response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).contains("audio/flac");
            return response.body();
        }
    }

    private static String sha256Of(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Map<String, String> query(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(
                        Collectors.toUnmodifiableMap(
                                parts -> parts[0],
                                parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
    }

    private static String bucket() {
        return ConfigProvider.getConfig().getValue("abservice.private-audio.bucket", String.class);
    }

    /** 期限はDB時計で判定するため、DB上の期限を3分後へ動かして検査する。 */
    private static void expireSoon(String deviceId) throws Exception {
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.prepareStatement(
                        "UPDATE listening_device SET expires_at = clock_timestamp() + interval '3 minutes'"
                                + " WHERE device_id = ?")) {
            statement.setObject(1, UUID.fromString(deviceId));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }
}
