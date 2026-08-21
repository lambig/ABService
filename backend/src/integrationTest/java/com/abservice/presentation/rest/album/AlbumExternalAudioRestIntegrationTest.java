package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバム集約内の外部音源 REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/albums/{albumId}/external-audios}（追加）→ {@code PUT
 * .../external-audios/order}（表示順変更）→ {@code DELETE
 * .../external-audios/{externalAudioId}}（削除）の疎通と、照会APIへの反映、許可外ホスト・URL重複・対象不在時の
 * 400／409／404 を RFC 9457 Problem Details 込みで確認する。実DB（Flyway
 * migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバム集約内の外部音源 REST エンドポイントの統合テスト")
class AlbumExternalAudioRestIntegrationTest {

    private static final String FIRST_URL = "https://soundcloud.com/example/first";
    private static final String SECOND_URL = "https://soundcloud.com/example/second";

    @Test
    @DisplayName("外部音源を追加すると201で表示順付きの情報が返る")
    void addExternalAudioSucceeds() {
        final String albumId = createAlbum("外部音源追加確認アルバム");

        authorized().contentType(ContentType.JSON).body(urlBody(FIRST_URL)).when()
                .post("/api/v1/albums/" + albumId + "/external-audios").then().statusCode(201)
                .body("albumId", equalTo(albumId)).body("displayOrder", equalTo(1))
                .body("url", equalTo(FIRST_URL));
    }

    @Test
    @DisplayName("許可されていないホストのURLは400 problem+json（検証エラー）を返す")
    void addExternalAudioWithDisallowedHostReturnsBadRequest() {
        final String albumId = createAlbum("外部音源ホスト検証確認アルバム");

        authorized().contentType(ContentType.JSON).body(urlBody("https://example.com/example/track")).when()
                .post("/api/v1/albums/" + albumId + "/external-audios").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("同じURLの追加は409 problem+jsonを返す")
    void addDuplicateExternalAudioReturnsConflict() {
        final String albumId = createAlbum("外部音源重複確認アルバム");
        addExternalAudio(albumId, FIRST_URL);

        authorized().contentType(ContentType.JSON).body(urlBody(FIRST_URL)).when()
                .post("/api/v1/albums/" + albumId + "/external-audios").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("存在しないアルバムへの追加は404 problem+jsonを返す")
    void addExternalAudioToNonExistentAlbumReturnsNotFound() {
        authorized().contentType(ContentType.JSON).body(urlBody(FIRST_URL)).when()
                .post("/api/v1/albums/" + UUID.randomUUID() + "/external-audios").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("認証なしの追加は401を返す")
    void addExternalAudioWithoutCredentialsReturnsUnauthorized() {
        final String albumId = createAlbum("外部音源認証確認アルバム");

        given().contentType(ContentType.JSON).body(urlBody(FIRST_URL)).when()
                .post("/api/v1/albums/" + albumId + "/external-audios").then().statusCode(401);
    }

    @Test
    @DisplayName("追加した外部音源は公開向け照会APIに表示順で並んで現れる")
    void externalAudiosAppearInPublicQuery() {
        final String albumId = createAlbum("外部音源照会確認アルバム");
        addExternalAudio(albumId, FIRST_URL);
        addExternalAudio(albumId, SECOND_URL);
        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200)
                .body("externalAudios", hasSize(2))
                .body("externalAudios[0].displayOrder", equalTo(1))
                .body("externalAudios[0].url", equalTo(FIRST_URL))
                .body("externalAudios[1].displayOrder", equalTo(2))
                .body("externalAudios[1].url", equalTo(SECOND_URL));
    }

    @Test
    @DisplayName("表示順を変更すると新しい順序で振り直される")
    void reorderExternalAudiosRenumbers() {
        final String albumId = createAlbum("外部音源順序変更確認アルバム");
        final String firstId = addExternalAudio(albumId, FIRST_URL);
        final String secondId = addExternalAudio(albumId, SECOND_URL);

        authorized().contentType(ContentType.JSON)
                .body("{\"orderedExternalAudioIds\":[\"" + secondId + "\",\"" + firstId + "\"]}").when()
                .put("/api/v1/albums/" + albumId + "/external-audios/order").then().statusCode(200)
                .body("albumId", equalTo(albumId))
                .body("externalAudios[0].externalAudioId", equalTo(secondId))
                .body("externalAudios[0].displayOrder", equalTo(1))
                .body("externalAudios[1].externalAudioId", equalTo(firstId))
                .body("externalAudios[1].displayOrder", equalTo(2));
    }

    @Test
    @DisplayName("件数と一致しない順序変更は409 problem+jsonを返す")
    void reorderExternalAudiosSizeMismatchReturnsConflict() {
        final String albumId = createAlbum("外部音源順序変更数不一致確認アルバム");
        addExternalAudio(albumId, FIRST_URL);

        authorized().contentType(ContentType.JSON)
                .body("{\"orderedExternalAudioIds\":[\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID() + "\"]}")
                .when().put("/api/v1/albums/" + albumId + "/external-audios/order").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("削除すると204が返り表示順が詰め直され、再度の削除は409 problem+jsonを返す（べき等ではない）")
    void removeExternalAudioRenumbersAndIsNotIdempotent() {
        final String albumId = createAlbum("外部音源削除確認アルバム");
        final String firstId = addExternalAudio(albumId, FIRST_URL);
        addExternalAudio(albumId, SECOND_URL);

        authorized().when().delete("/api/v1/albums/" + albumId + "/external-audios/" + firstId).then()
                .statusCode(204);

        authorized().when().get("/api/v1/admin/albums/" + albumId).then().statusCode(200)
                .body("externalAudios", hasSize(1))
                .body("externalAudios[0].url", equalTo(SECOND_URL))
                .body("externalAudios[0].displayOrder", equalTo(1));

        authorized().when().delete("/api/v1/albums/" + albumId + "/external-audios/" + firstId).then()
                .statusCode(409).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    private static String urlBody(String url) {
        return "{\"url\":\"" + url + "\"}";
    }

    private static String createAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static String addExternalAudio(String albumId, String url) {
        return authorized().contentType(ContentType.JSON).body(urlBody(url)).when()
                .post("/api/v1/albums/" + albumId + "/external-audios").then().statusCode(201).extract()
                .path("externalAudioId");
    }
}
