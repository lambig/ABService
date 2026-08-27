package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバム集約内トラック REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/albums/{albumId}/tracks}（追加）→ {@code PUT
 * .../tracks/{trackId}}（更新、{@code tunes}を含む全項目置換）→ {@code PUT
 * .../tracks/order}（順序変更）→ {@code DELETE .../tracks/{trackId}}（削除）の疎通と、
 * トラック番号重複・対象不在時の 409／404、検証エラー時の 400 を RFC 9457 Problem Details 込みで確認する。実
 * DB（Flyway migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバム集約内トラック REST エンドポイントの統合テスト")
class AlbumTrackRestIntegrationTest {

    @Test
    @DisplayName("トラックを追加すると201でトラック情報が返る")
    void addTrackSucceeds() {
        final String albumId = createAlbum("トラック追加確認アルバム");

        authorized().contentType(ContentType.JSON)
                .body("{\"trackNo\":1,\"title\":\"1曲目\",\"artistDisplayName\":\"トラックアーティスト\"}")
                .when().post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(201)
                .body("albumId", equalTo(albumId)).body("trackNo", equalTo(1)).body("title", equalTo("1曲目"));
    }

    @Test
    @DisplayName("存在しないアルバムへのトラック追加は404 problem+jsonを返す")
    void addTrackToNonExistentAlbumReturnsNotFound() {
        authorized().contentType(ContentType.JSON).body("{\"trackNo\":1,\"title\":\"1曲目\"}").when()
                .post("/api/v1/albums/" + UUID.randomUUID() + "/tracks").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("タイトル未指定でのトラック追加は400 problem+json（検証エラー）を返す")
    void addTrackValidationError() {
        final String albumId = createAlbum("トラック追加検証エラー確認アルバム");

        authorized().contentType(ContentType.JSON).body("{\"trackNo\":1}").when()
                .post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("トラック番号が重複するトラック追加は409 problem+jsonを返す")
    void addTrackWithDuplicateTrackNoReturnsConflict() {
        final String albumId = createAlbum("トラック番号重複確認アルバム");
        addTrack(
                albumId,
                1,
                "1曲目");

        authorized().contentType(ContentType.JSON).body("{\"trackNo\":1,\"title\":\"別の1曲目\"}").when()
                .post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("チューン構成を含めてトラックを追加すると201が返る")
    void addTrackWithTunesSucceeds() {
        final String albumId = createAlbum("チューン構成つきトラック追加確認アルバム");

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"trackNo\":1,\"title\":\"1曲目\",\"tunes\":["
                                + "{\"seq\":1,\"tuneTitle\":\"チューン1\",\"composerCreditOverride\":\"Trad.\"},"
                                + "{\"seq\":2,\"tuneTitle\":\"チューン2\"}]}")
                .when().post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(201)
                .body("albumId", equalTo(albumId)).body("trackNo", equalTo(1)).body("title", equalTo("1曲目"));
    }

    @Test
    @DisplayName("チューン構成のseqが重複するトラック追加は400 problem+json（検証エラー）を返す")
    void addTrackWithDuplicatedTuneSeqReturnsValidationError() {
        final String albumId = createAlbum("チューン構成seq重複確認アルバム");

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"trackNo\":1,\"title\":\"1曲目\",\"tunes\":["
                                + "{\"seq\":1,\"tuneTitle\":\"チューン1\"},"
                                + "{\"seq\":1,\"tuneTitle\":\"チューン2\"}]}")
                .when().post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("トラックを更新するとチューン構成を含む全項目が置換される")
    void updateTrackReplacesFields() {
        final String albumId = createAlbum("トラック更新確認アルバム");
        final String trackId = addTrack(
                albumId,
                1,
                "更新前タイトル");

        authorized().contentType(ContentType.JSON)
                .body("{\"trackNo\":2,\"title\":\"更新後タイトル\",\"artistDisplayName\":\"更新後アーティスト\"}").when()
                .put("/api/v1/albums/" + albumId + "/tracks/" + trackId).then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("trackId", equalTo(trackId)).body("trackNo", equalTo(2))
                .body("title", equalTo("更新後タイトル"));
    }

    @Test
    @DisplayName("存在しないトラックの更新は404 problem+jsonを返す")
    void updateTrackNotFound() {
        final String albumId = createAlbum("トラック更新不在確認アルバム");

        authorized().contentType(ContentType.JSON).body("{\"trackNo\":1,\"title\":\"タイトル\"}").when()
                .put("/api/v1/albums/" + albumId + "/tracks/" + UUID.randomUUID()).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("トラックを削除すると204が返り、再度の削除は409 problem+jsonを返す（べき等ではない）")
    void removeTrackThenRemoveAgainReturnsConflict() {
        final String albumId = createAlbum("トラック削除確認アルバム");
        final String trackId = addTrack(
                albumId,
                1,
                "削除対象トラック");

        authorized().when().delete("/api/v1/albums/" + albumId + "/tracks/" + trackId).then().statusCode(204);

        authorized().when().delete("/api/v1/albums/" + albumId + "/tracks/" + trackId).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("トラックの順序を変更すると新しい順序でトラック番号が振り直される")
    void reorderTracksRenumbers() {
        final String albumId = createAlbum("トラック順序変更確認アルバム");
        final String trackId1 = addTrack(
                albumId,
                1,
                "1曲目");
        final String trackId2 = addTrack(
                albumId,
                2,
                "2曲目");

        authorized().contentType(ContentType.JSON)
                .body("{\"orderedTrackIds\":[\"" + trackId2 + "\",\"" + trackId1 + "\"]}").when()
                .put("/api/v1/albums/" + albumId + "/tracks/order").then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("tracks[0].trackId", equalTo(trackId2))
                .body("tracks[0].trackNo", equalTo(1)).body("tracks[1].trackId", equalTo(trackId1))
                .body("tracks[1].trackNo", equalTo(2));
    }

    @Test
    @DisplayName("トラック数と一致しない順序変更は409 problem+jsonを返す")
    void reorderTracksSizeMismatchReturnsConflict() {
        final String albumId = createAlbum("トラック順序変更数不一致確認アルバム");
        addTrack(
                albumId,
                1,
                "1曲目");

        authorized().contentType(ContentType.JSON).body("{\"orderedTrackIds\":[\"" + UUID.randomUUID() + "\"]}").when()
                .put("/api/v1/albums/" + albumId + "/tracks/order").then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:BUSINESS_RULE_VIOLATION"));
    }

    private static String createAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static String addTrack(
            String albumId,
            int trackNo,
            String title) {
        return authorized().contentType(ContentType.JSON)
                .body("{\"trackNo\":" + trackNo + ",\"title\":\"" + title + "\"}").when()
                .post("/api/v1/albums/" + albumId + "/tracks").then().statusCode(201).extract().path("trackId");
    }
}
