package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバム REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/albums}（作成）→ {@code GET /api/v1/albums/{id}}（詳細）→
 * {@code PUT /api/v1/albums/{id}}（更新）→ {@code POST
 * /api/v1/albums/{id}/publish}（公開）→ {@code POST
 * /api/v1/albums/{id}/unpublish}（非公開化）→ {@code DELETE
 * /api/v1/albums/{id}}（削除）の疎通と、 {@code GET /api/v1/albums}（一覧、ページネーション付き）、未存在時の
 * 404、検証エラー時の 400 を RFC 9457 Problem Details 込みで確認する。実 DB（Flyway
 * migrate-at-start）で動作する。GET 単体取得・一覧取得は認証を伴わない公開向けQueryのため、作成直後の下書き（未公開） アルバムは
 * 404／一覧除外となる（下書きを含めた閲覧は認証必須の別経路で提供予定、#116）。非公開化に伴う参照記事のカスケード
 * 非公開化は{@code UnpublishAlbumServiceIntegrationTest}で検証する（REST経由ではアルバム記事の作成手段が未提供のため）。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバム REST エンドポイントの統合テスト")
class AlbumRestIntegrationTest {

    @Test
    @DisplayName("アルバムを作成すると下書き状態になり、公開向けAPIでのID詳細取得は404になる")
    void createThenGetIsNotFoundWhileUnpublished() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"E2Eテストアルバム\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"E2Eアーティスト\","
                                + "\"catalogNumber\":\"E2E-0001\",\"event\":{\"name\":\"コミックマーケット104\","
                                + "\"date\":\"2026-01-01\",\"place\":\"東京ビッグサイト\",\"spaceNumber\":\"東ホ-01a\"}}")
                .when().post("/api/v1/albums").then().statusCode(201).body("title", equalTo("E2Eテストアルバム"))
                .body("releaseDate", equalTo("2026-01-01")).body("artistDisplayName", equalTo("E2Eアーティスト")).extract()
                .path("albumId");

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("存在しないIDは404 problem+jsonを返す")
    void getNotFound() {
        given().when().get("/api/v1/albums/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"))
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("タイトル空白は400 problem+json（検証エラー）を返す")
    void createValidationError() {
        authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"   \",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR")).body("status", equalTo(400))
                .body("errors", not(empty())).body("errors[0].field", equalTo("value"));
    }

    @Test
    @DisplayName("アルバムを更新すると全項目置換され、トラックは変化しない（下書きのままなので公開向けGETは404）")
    void updateReplacesFieldsAndPreservesTracks() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"更新前タイトル\",\"releaseDate\":\"2025-01-01\","
                                + "\"artistDisplayName\":\"更新前アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"更新後タイトル\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"更新後アーティスト\",\"catalogNumber\":\"UPD-0001\"}")
                .when().put("/api/v1/albums/" + albumId).then().statusCode(200).body("albumId", equalTo(albumId))
                .body("title", equalTo("更新後タイトル")).body("releaseDate", equalTo("2026-01-01"))
                .body("artistDisplayName", equalTo("更新後アーティスト"));

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404);
    }

    @Test
    @DisplayName("存在しないIDの更新は404 problem+jsonを返す")
    void updateNotFound() {
        authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"タイトル\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .put("/api/v1/albums/" + UUID.randomUUID()).then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("タイトル空白での更新は400 problem+json（検証エラー）を返す")
    void updateValidationError() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"タイトル\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"   \",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .put("/api/v1/albums/" + albumId).then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("value"));
    }

    @Test
    @DisplayName("アルバムを公開すると公開向けGETで参照できるようになる")
    void publishThenGetSucceeds() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"公開確認アルバム\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("published", equalTo(true));

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200).body("albumId", equalTo(albumId));
    }

    @Test
    @DisplayName("公開済みアルバムを非公開化すると公開向けGETは404になり、参照記事がなければカスケード対象も空")
    void unpublishThenGetNotFound() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"非公開化確認アルバム\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().post("/api/v1/albums/" + albumId + "/publish").then().statusCode(200);

        authorized().when().post("/api/v1/albums/" + albumId + "/unpublish").then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("published", equalTo(false))
                .body("cascadeUnpublishedArticles", empty());

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404);
    }

    @Test
    @DisplayName("存在しないIDの公開は404 problem+jsonを返す")
    void publishNotFound() {
        authorized().when().post("/api/v1/albums/" + UUID.randomUUID() + "/publish").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("存在しないIDの非公開化は404 problem+jsonを返す")
    void unpublishNotFound() {
        authorized().when().post("/api/v1/albums/" + UUID.randomUUID() + "/unpublish").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("アルバムを削除すると以後のGETは404になる")
    void deleteThenGetNotFound() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"削除対象\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(204);

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(404);
    }

    @Test
    @DisplayName("削除はべき等で、存在しないIDの削除も204を返す")
    void deleteIsIdempotent() {
        final String albumId = authorized().contentType(ContentType.JSON)
                .body("{\"title\":\"べき等確認\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");

        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(204);
        authorized().when().delete("/api/v1/albums/" + albumId).then().statusCode(204);
        authorized().when().delete("/api/v1/albums/" + UUID.randomUUID()).then().statusCode(204);
    }

    @Test
    @DisplayName("一覧（公開向けAPI）は下書きアルバムを含まず、件数は作成しても増加しない")
    void listExcludesUnpublishedAlbums() {
        final int before = given().when().get("/api/v1/albums?page=0&size=1").then().statusCode(200).extract()
                .path("totalElements");

        final List<String> createdIds = List.of(
                createAlbum("一覧確認アルバム1"),
                createAlbum("一覧確認アルバム2"),
                createAlbum("一覧確認アルバム3"));

        final var response = given().when().get("/api/v1/albums?page=0&size=100").then().statusCode(200)
                .body("totalElements", equalTo(before)).extract();

        final List<String> ids = response.path("items.albumId");
        assertThat(ids).doesNotContainAnyElementsOf(createdIds);
    }

    @Test
    @DisplayName("一覧のpage/sizeを省略するとデフォルト値（page=0, size=20）が使われる")
    void listUsesDefaultPageAndSizeWhenOmitted() {
        given().when().get("/api/v1/albums").then().statusCode(200).body("page", equalTo(0))
                .body("size", equalTo(20));
    }

    private static String createAlbum(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"" + title + "\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"アーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }
}
