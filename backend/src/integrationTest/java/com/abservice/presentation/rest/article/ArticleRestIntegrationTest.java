package com.abservice.presentation.rest.article;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 記事 REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/articles}（作成）→ {@code GET
 * /api/v1/articles/{id}}（詳細）の疎通と、未存在時の 404、 検証エラー時の 400 を RFC 9457 Problem
 * Details 込みで確認する。実 DB（Flyway migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@DisplayName("記事 REST エンドポイントの統合テスト")
class ArticleRestIntegrationTest {

    @Test
    @DisplayName("記事を作成し、IDで詳細を取得できる")
    void createThenGet() {
        final String articleId = given().contentType(ContentType.JSON)
                .body(
                        "{\"articleType\":\"NOTE\",\"title\":\"E2Eテスト記事\",\"body\":\"本文\",\"bodyFormat\":\"MARKDOWN\","
                                + "\"introShort\":\"概要\"}")
                .when().post("/api/v1/articles").then().statusCode(201).body("articleType", equalTo("NOTE"))
                .body("title", equalTo("E2Eテスト記事")).body("publicFlag", equalTo(false)).extract()
                .path("articleId");

        given().when().get("/api/v1/articles/" + articleId).then().statusCode(200)
                .body("articleId", equalTo(articleId)).body("title", equalTo("E2Eテスト記事"))
                .body("body", equalTo("本文")).body("bodyFormat", equalTo("MARKDOWN"));
    }

    @Test
    @DisplayName("存在しないIDは404 problem+jsonを返す")
    void getNotFound() {
        given().when().get("/api/v1/articles/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"))
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("タイトル空白は400 problem+json（検証エラー）を返す")
    void createValidationError() {
        given().contentType(ContentType.JSON).body("{\"articleType\":\"NOTE\",\"title\":\"   \"}").when()
                .post("/api/v1/articles").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR")).body("status", equalTo(400))
                .body("errors", not(empty())).body("errors[0].field", equalTo("title"));
    }
}
