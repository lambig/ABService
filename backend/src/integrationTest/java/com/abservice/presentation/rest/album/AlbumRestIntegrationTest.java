package com.abservice.presentation.rest.album;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アルバム REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/albums}（作成）→ {@code GET
 * /api/v1/albums/{id}}（詳細）の疎通と、未存在時の 404、 検証エラー時の 400 を RFC 9457 Problem
 * Details 込みで確認する。実 DB（Flyway migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@DisplayName("アルバム REST エンドポイントの統合テスト")
class AlbumRestIntegrationTest {

    @Test
    @DisplayName("アルバムを作成し、IDで詳細を取得できる")
    void createThenGet() {
        final String albumId = given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"E2Eテストアルバム\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"E2Eアーティスト\","
                                + "\"catalogNumber\":\"E2E-0001\",\"event\":{\"name\":\"コミックマーケット104\","
                                + "\"date\":\"2026-01-01\",\"place\":\"東京ビッグサイト\",\"spaceNumber\":\"東ホ-01a\"}}")
                .when().post("/api/v1/albums").then().statusCode(201).body("title", equalTo("E2Eテストアルバム"))
                .body("releaseDate", equalTo("2026-01-01")).body("artistDisplayName", equalTo("E2Eアーティスト")).extract()
                .path("albumId");

        given().when().get("/api/v1/albums/" + albumId).then().statusCode(200).body("albumId", equalTo(albumId))
                .body("title", equalTo("E2Eテストアルバム")).body("catalogNumber", equalTo("E2E-0001"))
                .body("eventName", equalTo("コミックマーケット104")).body("eventDate", equalTo("2026-01-01"))
                .body("eventPlace", equalTo("東京ビッグサイト")).body("eventSpaceNumber", equalTo("東ホ-01a"));
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
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"   \",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"アーティスト\"}").when()
                .post("/api/v1/albums").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR")).body("status", equalTo(400))
                .body("errors", not(empty())).body("errors[0].field", equalTo("value"));
    }
}
