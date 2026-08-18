package com.abservice.presentation.rest.albumarticle;

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
 * アルバム記事 REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/album-articles}（作成）→ {@code GET
 * /api/v1/album-articles/{id}}（詳細）→ {@code PUT
 * /api/v1/album-articles/{id}}（更新）→ {@code DELETE
 * /api/v1/album-articles/{id}}（削除）の疎通と、 {@code GET
 * /api/v1/album-articles}（一覧、ページネーション付き）、未存在時の 404、検証エラー時の 400 を RFC 9457
 * Problem Details 込みで確認する。 実 DB（Flyway migrate-at-start）で動作する。アルバム記事は既存の Album
 * を参照するため、事前に Album を作成する。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバム記事 REST エンドポイントの統合テスト")
class AlbumArticleRestIntegrationTest {

    @Test
    @DisplayName("アルバム記事を作成し、IDで詳細を取得できる")
    void createThenGet() {
        final String albumId = createAlbum();

        given().contentType(ContentType.JSON)
                .body(
                        "{\"albumId\":\"" + albumId
                                + "\",\"introShort\":\"お品書き用コメント\",\"firstEventSpace\":\"東X-00b\",\"labelTag\":\"NEW\","
                                + "\"distribution\":{\"physicalPrice\":1000,\"downloadPrice\":500}}")
                .when().post("/api/v1/album-articles").then().statusCode(201).body("albumId", equalTo(albumId))
                .body("introShort", equalTo("お品書き用コメント")).body("labelTag", equalTo("NEW"));

        given().when().get("/api/v1/album-articles/" + albumId).then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("introShort", equalTo("お品書き用コメント"))
                .body("firstEventSpace", equalTo("東X-00b")).body("labelTag", equalTo("NEW"));
    }

    @Test
    @DisplayName("存在しないIDは404 problem+jsonを返す")
    void getNotFound() {
        given().when().get("/api/v1/album-articles/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"))
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("albumId未指定は400 problem+json（検証エラー）を返す")
    void createValidationError() {
        given().contentType(ContentType.JSON).body("{\"introShort\":\"コメント\"}").when()
                .post("/api/v1/album-articles").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR")).body("status", equalTo(400))
                .body("errors", not(empty())).body("errors[0].field", equalTo("albumId"));
    }

    private static String createAlbum() {
        return given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"E2E記事テスト用アルバム\",\"releaseDate\":\"2026-01-01\","
                                + "\"artistDisplayName\":\"E2Eアーティスト\"}")
                .when().post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    @Test
    @DisplayName("アルバム記事を更新すると全項目置換される")
    void updateReplacesFields() {
        final String albumId = createAlbum();
        given().contentType(ContentType.JSON)
                .body("{\"albumId\":\"" + albumId + "\",\"introShort\":\"更新前コメント\"}").when()
                .post("/api/v1/album-articles").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("{\"introShort\":\"更新後コメント\",\"firstEventSpace\":\"東X-01a\",\"labelTag\":\"NEW\"}")
                .when().put("/api/v1/album-articles/" + albumId).then().statusCode(200)
                .body("albumId", equalTo(albumId)).body("introShort", equalTo("更新後コメント"))
                .body("labelTag", equalTo("NEW"));

        given().when().get("/api/v1/album-articles/" + albumId).then().statusCode(200)
                .body("introShort", equalTo("更新後コメント")).body("firstEventSpace", equalTo("東X-01a"));
    }

    @Test
    @DisplayName("存在しないIDの更新は404 problem+jsonを返す")
    void updateNotFound() {
        given().contentType(ContentType.JSON).body("{\"introShort\":\"コメント\"}").when()
                .put("/api/v1/album-articles/" + UUID.randomUUID()).then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("labelTagが不正な値での更新は400 problem+json（検証エラー）を返す")
    void updateValidationError() {
        final String albumId = createAlbum();
        given().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}").when()
                .post("/api/v1/album-articles").then().statusCode(201);

        given().contentType(ContentType.JSON).body("{\"labelTag\":\"BAD\"}").when()
                .put("/api/v1/album-articles/" + albumId).then().statusCode(400)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("labelTag"));
    }

    @Test
    @DisplayName("アルバム記事を削除すると以後のGETは404になる")
    void deleteThenGetNotFound() {
        final String albumId = createAlbum();
        given().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}").when()
                .post("/api/v1/album-articles").then().statusCode(201);

        given().when().delete("/api/v1/album-articles/" + albumId).then().statusCode(204);

        given().when().get("/api/v1/album-articles/" + albumId).then().statusCode(404);
    }

    @Test
    @DisplayName("削除はべき等で、存在しないIDの削除も204を返す")
    void deleteIsIdempotent() {
        final String albumId = createAlbum();
        given().contentType(ContentType.JSON).body("{\"albumId\":\"" + albumId + "\"}").when()
                .post("/api/v1/album-articles").then().statusCode(201);

        given().when().delete("/api/v1/album-articles/" + albumId).then().statusCode(204);
        given().when().delete("/api/v1/album-articles/" + albumId).then().statusCode(204);
        given().when().delete("/api/v1/album-articles/" + UUID.randomUUID()).then().statusCode(204);
    }

    @Test
    @DisplayName("一覧はページネーション付きで返り、件数は作成分だけ増加する")
    void listReturnsPaginatedResultsAndCountIncreasesByCreated() {
        final int size = 100;
        final int before = given().when().get("/api/v1/album-articles?page=0&size=1").then().statusCode(200)
                .extract().path("totalElements");

        final List<String> createdIds = List.of(
                createAlbumArticle("一覧確認記事1"),
                createAlbumArticle("一覧確認記事2"),
                createAlbumArticle("一覧確認記事3"));

        final int after = given().when().get("/api/v1/album-articles?page=0&size=1").then().statusCode(200)
                .extract().path("totalElements");
        assertThat(after).isEqualTo(before + createdIds.size());

        final int totalPages = (int) Math.ceil((double) after / size);
        final int lastPage = Math.max(totalPages - 1, 0);
        final var response = given().when().get("/api/v1/album-articles?page=" + lastPage + "&size=" + size)
                .then().statusCode(200).body("page", equalTo(lastPage)).body("size", equalTo(size))
                .body("totalElements", equalTo(after)).body("totalPages", equalTo(totalPages)).extract();

        final List<String> ids = response.path("items.albumId");
        assertThat(ids).containsAll(createdIds);
    }

    @Test
    @DisplayName("一覧のpage/sizeを省略するとデフォルト値（page=0, size=20）が使われる")
    void listUsesDefaultPageAndSizeWhenOmitted() {
        given().when().get("/api/v1/album-articles").then().statusCode(200).body("page", equalTo(0))
                .body("size", equalTo(20));
    }

    private static String createAlbumArticle(String introShort) {
        final String albumId = createAlbum();
        given().contentType(ContentType.JSON)
                .body("{\"albumId\":\"" + albumId + "\",\"introShort\":\"" + introShort + "\"}").when()
                .post("/api/v1/album-articles").then().statusCode(201);
        return albumId;
    }
}
