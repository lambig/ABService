package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@QuarkusTest
@TestProfile(ListeningPackageRestIntegrationTest.Enabled.class)
@ExtendWith(CleanDatabase.class)
@DisplayName("端末向け配布パッケージのHTTP契約")
class ListeningPackageRestIntegrationTest {
    private static final String PACKAGE = "/api/v1/listening/package";
    private static final String DEVICES = "/api/v1/admin/listening-devices";
    private static final String REGISTRATIONS = "/api/v1/admin/private-audio/registrations";
    private static final List<Integer> MIN_APP_VERSION = List.of(
            1,
            10,
            0);
    private static final List<Integer> MAX_APP_VERSION = List.of(
            2,
            0,
            0);
    /** 1x1 の PNG。検査用プロファイルの上限（1024 バイト）に収まる。 */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC");

    /** 一時領域はJVMの一時ディレクトリ配下に取る（固定の /tmp は macOS では symlink 経由になるため）。 */
    public static class Enabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "abservice.private-audio.enabled",
                    "true",
                    "abservice.private-audio.bucket",
                    "synthetic-listening-package",
                    "abservice.private-audio.temporary-directory",
                    System.getProperty("java.io.tmpdir") + "/abservice-listening-package-test",
                    "abservice.private-audio.maintenance-interval",
                    "PT1H");
        }
    }

    @Test
    @DisplayName("公開済みで確定音源が関連付いた作品だけをManifest v2として返し、URLや保存キーを含めない")
    void buildsManifestFromPublishedAlbumsWithConfirmedCrossfade() throws Exception {
        final String token = device();
        final String published = albumWithTracks("公開作品");
        final String draft = albumWithTracks("下書き作品");
        final String withoutAudio = albumWithTracks("音源なし作品");
        final String audio = confirmed(100, "a");
        final String draftAudio = confirmed(200, "b");
        assign(
                published,
                audio,
                0);
        assign(
                draft,
                draftAudio,
                0);
        publish(published);
        publish(withoutAudio);

        final var response = asDevice(token).get(PACKAGE).then().statusCode(200).contentType(ContentType.JSON)
                .header("Cache-Control", "no-store")
                .body("schemaVersion", equalTo(2))
                .body("compatibleAppVersion.minInclusive", equalTo(MIN_APP_VERSION))
                .body("compatibleAppVersion.maxExclusive", equalTo(MAX_APP_VERSION))
                .body("presentationAssetIds", hasSize(0))
                .body("albums", hasSize(1)).body("albums[0].albumId", equalTo(published))
                .body("albums[0].title", equalTo("公開作品"))
                .body("albums[0].tracks", hasSize(2))
                .body("albums[0].tracks[0].trackNo", equalTo(1)).body("albums[0].tracks[0].title", equalTo("1曲目"))
                .body("albums[0].tracks[1].trackNo", equalTo(2))
                .body("albums[0].tracks[1].title", equalTo("Reel / Jig"))
                .body("assets", hasSize(1)).body("assets[0].assetId", equalTo(audio))
                .body("assets[0].mediaType", equalTo("audio/flac")).body("assets[0].byteLength", equalTo(100))
                .body("assets[0].checksum.algorithm", equalTo("sha256"))
                .body("assets[0].checksum.value", equalTo("a".repeat(64))).body("assets[0].required", equalTo(true))
                .body("playbackItems", hasSize(1))
                .body("playbackItems[0].playbackItemId", equalTo("album-crossfade:" + published))
                .body("playbackItems[0].kind", equalTo("album-crossfade"))
                .body("playbackItems[0].albumId", equalTo(published))
                .body("playbackItems[0].title", equalTo("公開作品"))
                .body("playbackItems[0].audioAssetId", equalTo(audio))
                .body("playbackItems[0].durationSeconds", equalTo(1.0f))
                .extract();
        final String version = response.path("packageVersion");
        assertThat(version).matches("^[0-9a-f]{64}$");
        assertThat(response.header("ETag")).isEqualTo("\"" + version + "\"");
        assertThat(response.asString()).doesNotContainIgnoringCase("http").doesNotContainIgnoringCase("url")
                .doesNotContain("storageKey").doesNotContain("audio/verified");
        assertThat(response.asString()).doesNotContain(draft).doesNotContain(withoutAudio);
    }

    @Test
    @DisplayName("同じ内容は同じ版で、関連付けの差し替えと非公開化は版を変える")
    void versionFollowsContent() throws Exception {
        final String token = device();
        final String album = albumWithTracks("版の作品");
        final String first = confirmed(100, "a");
        final String second = confirmed(300, "c");
        assign(
                album,
                first,
                0);
        publish(album);
        final String initial = version(token);
        assertThat(version(token)).isEqualTo(initial);
        assign(
                album,
                second,
                1);
        final String replaced = version(token);
        assertThat(replaced).isNotEqualTo(initial);
        asDevice(token).get(PACKAGE).then().statusCode(200)
                .body("playbackItems[0].playbackItemId", equalTo("album-crossfade:" + album))
                .body("playbackItems[0].audioAssetId", equalTo(second))
                .body("assets[0].byteLength", equalTo(300));
        authorized().post("/api/v1/albums/" + album + "/unpublish").then().statusCode(200);
        asDevice(token).get(PACKAGE).then().statusCode(200).body("albums", hasSize(0))
                .body("assets", hasSize(0)).body("playbackItems", hasSize(0));
        assertThat(version(token)).isNotIn(initial, replaced);
    }

    @Test
    @DisplayName("作品は公開サイトの一覧と同じ順（カタログナンバーの降順、未付与は末尾）で並ぶ")
    void ordersAlbumsLikePublicCatalog() throws Exception {
        final String token = device();
        final String unnumbered = albumWithTracks("番号なし");
        final String older = albumWithCatalogNumber("旧作", "AB-001");
        final String newer = albumWithCatalogNumber("新作", "AB-002");
        assign(
                unnumbered,
                confirmed(100, "a"),
                0);
        assign(
                older,
                confirmed(200, "b"),
                0);
        assign(
                newer,
                confirmed(300, "c"),
                0);
        publish(unnumbered);
        publish(older);
        publish(newer);

        asDevice(token).get(PACKAGE).then().statusCode(200)
                .body(
                        "albums.albumId",
                        contains(
                                newer,
                                older,
                                unnumbered))
                .body(
                        "playbackItems.albumId",
                        contains(
                                newer,
                                older,
                                unnumbered))
                .body(
                        "assets.byteLength",
                        contains(
                                300,
                                200,
                                100));
    }

    @Test
    @DisplayName("確定時に実測値を記録したカバー画像だけを表示素材として載せ、記録の無い画像の作品は項目ごと省く")
    void carriesConfirmedArtworkOnly() throws Exception {
        final String token = device();
        final String artwork = confirmedImage();
        final String withArtwork = albumWithTracks("画像あり", "\"coverImageKey\":\"" + artwork + "\",");
        final String legacy = albumWithTracks("記録なし", "\"coverImageKey\":\"legacy-cover.png\",");
        assign(
                withArtwork,
                confirmed(100, "a"),
                0);
        assign(
                legacy,
                confirmed(200, "b"),
                0);
        publish(withArtwork);
        publish(legacy);

        final var response = asDevice(token).get(PACKAGE).then().statusCode(200)
                .body("albums", hasSize(2))
                .body("albums.find { it.albumId == '" + withArtwork + "' }.artworkAssetId", equalTo(artwork))
                .body("albums.find { it.albumId == '" + legacy + "' }", not(hasKey("artworkAssetId")))
                .body("presentationAssetIds", equalTo(List.of(artwork)))
                .body("assets", hasSize(3))
                .body("assets.find { it.assetId == '" + artwork + "' }.mediaType", equalTo("image/png"))
                .body("assets.find { it.assetId == '" + artwork + "' }.byteLength", equalTo(PNG.length))
                .body("assets.find { it.assetId == '" + artwork + "' }.checksum.value", equalTo(sha256Of(PNG)))
                .body("assets.find { it.assetId == '" + artwork + "' }.required", equalTo(true))
                .extract();
        assertThat(response.asString()).doesNotContain("legacy-cover.png");

        // 故障注入: 記録だけが失われた確定済み画像を作り、同じキーの再確定で補えることを見る。
        execute("DELETE FROM published_asset WHERE asset_key = '" + artwork + "'");
        asDevice(token).get(PACKAGE).then().statusCode(200).body("presentationAssetIds", hasSize(0));
        authorized().post("/api/v1/assets/" + artwork + "/confirm").then().statusCode(200)
                .body("assetKey", equalTo(artwork)).body("sizeBytes", equalTo(PNG.length));
        asDevice(token).get(PACKAGE).then().statusCode(200)
                .body("presentationAssetIds", equalTo(List.of(artwork)));
        authorized().post("/api/v1/assets/" + artwork + "/confirm").then().statusCode(409);
    }

    private static void execute(String sql) throws Exception {
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.createStatement()) {
            assertThat(statement.executeUpdate(sql)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("配布パッケージは端末だけが取得でき、管理者は403・無認証は401になる")
    void onlyDevicesCanRead() {
        final String token = device();
        given().get(PACKAGE).then().statusCode(401).contentType("application/problem+json");
        authorized().get(PACKAGE).then().statusCode(403).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:FORBIDDEN"));
        asDevice(token).get(PACKAGE).then().statusCode(200).body("albums", hasSize(0));
        given().get("/api/v1/listening/package").then().statusCode(401);
    }

    private String version(String token) {
        return asDevice(token).get(PACKAGE).then().statusCode(200).extract().path("packageVersion");
    }

    private static String device() {
        return authorized().contentType(ContentType.JSON).body(Map.of("label", "検査端末")).post(DEVICES).then()
                .statusCode(201).extract().path("token");
    }

    private static RequestSpecification asDevice(String token) {
        return given().header("Authorization", "Bearer " + token);
    }

    private static String albumWithTracks(String title) {
        return albumWithTracks(title, "");
    }

    private static String albumWithCatalogNumber(String title, String catalogNumber) {
        return albumWithTracks(title, "\"catalogNumber\":\"" + catalogNumber + "\",");
    }

    /** 1曲目は曲名を持ち、2曲目は曲名を持たずチューン名だけを持つ。 */
    private static String albumWithTracks(String title, String extraFields) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\"," + extraFields + "\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"Fixture\",\"tracks\":["
                                + "{\"title\":\"1曲目\"},"
                                + "{\"tunes\":[{\"tuneTitle\":\"Reel\"},{\"tuneTitle\":\"Jig\"}]}]}")
                .post("/api/v1/albums/with-tracks").then().statusCode(201).extract().path("albumId");
    }

    private static void publish(String album) {
        authorized().post("/api/v1/albums/" + album + "/publish").then().statusCode(200);
    }

    private static ExtractableResponse<Response> assign(
            String album,
            String audio,
            int revision) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "audioId",
                                audio,
                                "expectedRevision",
                                revision))
                .put("/api/v1/admin/albums/" + album + "/listening-audio/crossfade").then().statusCode(200)
                .extract();
    }

    /** 画像は実際の経路（署名付きURLへの PUT と確定）で確定させ、確定時に記録した実測値が Manifest に載ることを見る。 */
    private static String confirmedImage() {
        final var issued = authorized().contentType(ContentType.JSON).body(Map.of("contentType", "image/png"))
                .post("/api/v1/assets/upload-url").then().statusCode(200).extract();
        final String assetKey = issued.path("assetKey");
        final String uploadUrl = issued.path("uploadUrl");
        given().config(
                RestAssured.config().encoderConfig(
                        EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .urlEncodingEnabled(false).contentType("image/png").body(PNG).put(uploadUrl).then()
                .statusCode(200);
        authorized().post("/api/v1/assets/" + assetKey + "/confirm").then().statusCode(200);
        return assetKey;
    }

    private static String sha256Of(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** FLAC実体の検査は基盤の統合試験で扱い、本試験は確定後の配布契約を検査する。 */
    private static String confirmed(int byteLength, String hexDigit) throws Exception {
        final String id = authorized().post(REGISTRATIONS).then().statusCode(201).extract().path("audioId");
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
            statement.setInt(1, byteLength);
            statement.setString(2, hexDigit.repeat(64));
            statement.setObject(3, UUID.fromString(id));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        return id;
    }
}
