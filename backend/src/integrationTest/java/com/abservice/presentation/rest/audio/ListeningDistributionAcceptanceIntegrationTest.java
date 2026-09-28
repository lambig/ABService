package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * #475 の完了条件を、実FLACの受信から端末の取得までの結合経路で固定する受け入れ試験。
 *
 * <p>
 * FLACは検査器と同じ {@code flac} CLI で合成し、管理APIの予約・受信・確定を実際に通す。端末は配布パッケージを取り、
 * 取得URLを解決して専用MinIOバケットから実体を取る。個々の経路の境界（認可・対象外・署名の資格情報）は各経路の統合試験が
 * 担い、ここでは経路をまたいだ一致・期限・再開・競合を見る。利用者は運用者1人で、競合はその人の編集と端末の準備が 重なる範囲を扱う。
 * </p>
 */
@QuarkusTest
@TestProfile(ListeningDistributionAcceptanceIntegrationTest.AudioRuntime.class)
@QuarkusTestResource(value = AudioHttpTestResource.class, restrictToAnnotatedClass = true)
@ExtendWith(CleanDatabase.class)
@DisplayName("試聴端末への実配布の受け入れ（#475 の完了条件）")
class ListeningDistributionAcceptanceIntegrationTest {
    /** 専用リソースでの再起動を通常テストと分離し、前の起動のReactiveセッション状態を引き継がない。 */
    public static class AudioRuntime implements QuarkusTestProfile {
    }

    private static final String PACKAGE = "/api/v1/listening/package";
    private static final String DEVICES = "/api/v1/admin/listening-devices";
    private static final String REGISTRATIONS = "/api/v1/admin/private-audio/registrations";
    @TempDir
    private Path directory;
    @TestHTTPResource
    private URI endpoint;

    @Test
    @DisplayName("実FLACを受信・確定した作品は、曲ごとの音源なしにクロスフェードだけで配布され、取得した実体は元のFLACと一致する")
    void deliversIngestedFlacEndToEnd() throws Exception {
        final String token = device().path("token");
        final var flac = FlacFixtures.encodedFile(
                directory,
                FlacFixtures.ONE_SECOND_OF_PCM,
                1);
        final String audio = ingest(flac);
        final String album = publishedAlbum("受け入れ作品");
        assign(
                album,
                audio,
                0);

        final var manifest = asDevice(token).get(PACKAGE).then().statusCode(200)
                .body("albums", hasSize(1)).body("albums[0].tracks", hasSize(2))
                .body("assets", hasSize(1)).body("playbackItems", hasSize(1))
                .body("playbackItems[0].kind", equalTo("album-crossfade"))
                .body("playbackItems[0].audioAssetId", equalTo(audio))
                .body("playbackItems[0].durationSeconds", equalTo(1.0f))
                .extract();
        final var expected = sha256Of(Files.readAllBytes(flac));
        assertThat(manifest.<String>path("assets[0].checksum.value")).isEqualTo(expected);
        assertThat(manifest.<Integer>path("assets[0].byteLength")).isEqualTo((int) Files.size(flac));

        final var fetched = fetch(url(token, audio));
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(sha256Of(fetched.body())).isEqualTo(expected);
    }

    @Test
    @DisplayName("URLの期限切れはS3が拒み、端末の期限切れ・失効は401になり、再発行した資格情報で同じ版の取得を再開できる")
    void resumesAfterExpiryAndRevocationWithReissuedCredential() throws Exception {
        final var flac = FlacFixtures.encodedFile(
                directory,
                FlacFixtures.ONE_SECOND_OF_PCM,
                2);
        final String audio = ingest(flac);
        assign(
                publishedAlbum("再開作品"),
                audio,
                0);
        final var expiring = device();
        final String expiringToken = expiring.path("token");
        final String version = version(expiringToken);

        final String expiringId = expiring.path("device.deviceId");
        expireIn(expiringId, Duration.ofSeconds(6));
        final var deviceExpiresAt = deviceExpiresAt(expiringId);
        final var shortLived = asDevice(expiringToken).get(urlPath(audio)).then().statusCode(200).extract();
        final var urlExpiresAt = Instant.parse(shortLived.path("expiresAt"));
        assertThat(urlExpiresAt).as("URLの期限は端末の期限を超えない").isBeforeOrEqualTo(deviceExpiresAt);
        waitUntilAfter(urlExpiresAt);
        assertThat(fetch(shortLived.path("url")).statusCode()).as("期限を過ぎたURLはS3が拒む").isEqualTo(403);
        waitUntilAfter(deviceExpiresAt);
        asDevice(expiringToken).get(PACKAGE).then().statusCode(401);
        asDevice(expiringToken).get(urlPath(audio)).then().statusCode(401);

        final var revoked = device();
        final String revokedToken = revoked.path("token");
        authorized().delete(DEVICES + "/" + revoked.path("device.deviceId")).then().statusCode(204);
        asDevice(revokedToken).get(PACKAGE).then().statusCode(401);
        asDevice(revokedToken).get(urlPath(audio)).then().statusCode(401);

        final String reissued = device().path("token");
        assertThat(version(reissued)).as("資格情報を替えても配布内容の版は同じ").isEqualTo(version);
        final var resumed = fetch(url(reissued, audio));
        assertThat(resumed.statusCode()).isEqualTo(200);
        assertThat(sha256Of(resumed.body())).isEqualTo(sha256Of(Files.readAllBytes(flac)));
    }

    @Test
    @DisplayName("端末の準備中に関連付けを差し替えても、各Manifestは一方の版として一貫し、旧音源の解決は止まり発行済みURLは使える")
    void staysConsistentWhileReassignedDuringPreparation() throws Exception {
        final String token = device().path("token");
        final String first = ingest(
                FlacFixtures.encodedFile(
                        directory,
                        FlacFixtures.ONE_SECOND_OF_PCM,
                        3));
        final String second = ingest(
                FlacFixtures.encodedFile(
                        directory,
                        FlacFixtures.ONE_SECOND_OF_PCM * 2,
                        4));
        final String album = publishedAlbum("差し替え作品");
        assign(
                album,
                first,
                0);
        final String firstVersion = version(token);
        final String issued = url(token, first);
        final Map<String, String> checksums = Map.of(
                first,
                checksumOf(first),
                second,
                checksumOf(second));

        final var preparation = CompletableFuture.supplyAsync(() -> packages(token, 30));
        assign(
                album,
                second,
                1);
        assign(
                album,
                first,
                2);
        assign(
                album,
                second,
                3);
        final List<JsonPath> observed = preparation.join();
        final String secondVersion = version(token);

        assertThat(secondVersion).isNotEqualTo(firstVersion);
        assertThat(observed).isNotEmpty().allSatisfy(
                manifest -> assertConsistent(
                        manifest,
                        checksums,
                        Map.of(
                                first,
                                firstVersion,
                                second,
                                secondVersion)));
        asDevice(token).get(urlPath(first)).then().statusCode(404);
        assertThat(fetch(issued).statusCode()).as("発行済みURLは期限まで使える").isEqualTo(200);
    }

    /** 再生項目の音源が assets にあり、checksum がその音源の確定値で、版がその音源の状態の版であること。 */
    private static void assertConsistent(
            JsonPath manifest,
            Map<String, String> checksums,
            Map<String, String> versions) {
        final String audio = manifest.getString("playbackItems[0].audioAssetId");
        assertThat(checksums).containsKey(audio);
        assertThat(manifest.getList("assets.assetId", String.class)).containsExactly(audio);
        assertThat(manifest.getString("assets[0].checksum.value")).isEqualTo(checksums.get(audio));
        assertThat(manifest.getString("packageVersion")).isEqualTo(versions.get(audio));
    }

    /** 準備を模して、端末がパッケージを続けて取り直す。管理操作と並行させるため RestAssured ではなく HttpClient を使う。 */
    private List<JsonPath> packages(String token, int times) {
        try (var client = HttpClient.newHttpClient()) {
            return Stream.generate(() -> packageOnce(client, token))
                    .limit(times)
                    .toList();
        }
    }

    private JsonPath packageOnce(HttpClient client, String token) {
        try {
            final var response = client.send(
                    HttpRequest.newBuilder(endpoint.resolve(PACKAGE))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return JsonPath.from(response.body());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String checksumOf(String audio) {
        return authorized().get(REGISTRATIONS + "/" + audio).then().statusCode(200).extract()
                .path("metadata.sha256");
    }

    private static String version(String token) {
        return asDevice(token).get(PACKAGE).then().statusCode(200).extract().path("packageVersion");
    }

    private static String url(String token, String audio) {
        return asDevice(token).get(urlPath(audio)).then().statusCode(200).extract().path("url");
    }

    private static String urlPath(String audio) {
        return PACKAGE + "/assets/" + audio + "/url";
    }

    private static RequestSpecification asDevice(String token) {
        return given().header("Authorization", "Bearer " + token);
    }

    private static ExtractableResponse<Response> device() {
        return authorized().contentType(ContentType.JSON).body(Map.of("label", "受け入れ端末")).post(DEVICES).then()
                .statusCode(201).extract();
    }

    /** 1曲目は曲名を持ち、2曲目はチューン名だけを持つ。曲ごとの音源は登録しない。 */
    private static String publishedAlbum(String title) {
        final String album = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"Fixture\",\"tracks\":["
                                + "{\"title\":\"1曲目\"},{\"tunes\":[{\"tuneTitle\":\"Reel\"}]}]}")
                .post("/api/v1/albums/with-tracks").then().statusCode(201).extract().path("albumId");
        authorized().post("/api/v1/albums/" + album + "/publish").then().statusCode(200);
        return album;
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

    /** 管理APIで予約し、実FLACを受信させて検査・保存・確定まで通す。 */
    private static String ingest(Path flac) throws Exception {
        final String id = authorized().post(REGISTRATIONS).then().statusCode(201).extract().path("audioId");
        authorized().contentType("audio/flac").body(Files.readAllBytes(flac))
                .put(REGISTRATIONS + "/" + id + "/content").then().statusCode(200)
                .body("state", equalTo("CONFIRMED"));
        return id;
    }

    private static HttpResponse<byte[]> fetch(String url) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static String sha256Of(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /**
     * 端末の期限を管理APIの一覧から読む。URLの期限は残り時間と署名時刻の秒単位の切り捨てで端末の期限より最大約2秒早くなるため、
     * 端末の期限切れはこの値を基準に待つ。
     */
    private static Instant deviceExpiresAt(String deviceId) {
        return Instant.parse(
                authorized().get(DEVICES).then().statusCode(200).extract()
                        .path("devices.find { it.deviceId == '" + deviceId + "' }.expiresAt"));
    }

    /** 期限は秒単位で判定されるため、期限の1秒後まで待つ。 */
    private static void waitUntilAfter(Instant expiresAt) throws InterruptedException {
        Thread.sleep(Math.max(0, Duration.between(Instant.now(), expiresAt.plusSeconds(1)).toMillis()));
    }

    /** 期限はDB時計で判定するため、DB上の期限を指定の時間後へ動かして検査する。 */
    private static void expireIn(String deviceId, Duration remaining) throws Exception {
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.prepareStatement(
                        "UPDATE listening_device SET expires_at = clock_timestamp() + make_interval(secs => ?)"
                                + " WHERE device_id = ?")) {
            statement.setLong(1, remaining.toSeconds());
            statement.setObject(2, UUID.fromString(deviceId));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }
}
