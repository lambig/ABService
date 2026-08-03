package com.abservice.presentation.rest.albumarticle;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバム記事 REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/album-articles}（作成）→ {@code GET
 * /api/v1/album-articles/{id}}（詳細） の疎通と、未存在時の 404、検証エラー時の 400 を RFC 9457
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
}
