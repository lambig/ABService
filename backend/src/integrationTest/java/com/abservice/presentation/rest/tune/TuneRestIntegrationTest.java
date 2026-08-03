package com.abservice.presentation.rest.tune;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * チューン REST エンドポイントの E2E 統合テスト
 *
 * <p>
 * {@code POST /api/v1/tunes}（作成）→ {@code GET /api/v1/tunes/{id}}（詳細）の疎通と、未存在時の
 * 404、 検証エラー時の 400 を RFC 9457 Problem Details 込みで確認する。実 DB（Flyway
 * migrate-at-start）で動作する。
 * </p>
 */
@QuarkusTest
@DisplayName("チューン REST エンドポイントの統合テスト")
class TuneRestIntegrationTest {

    @Test
    @DisplayName("チューンを作成し、IDで詳細を取得できる")
    void createThenGet() {
        final String tuneId = given().contentType(ContentType.JSON)
                .body(
                        "{\"title\":\"E2Eテストチューン\",\"tuneKind\":\"TRAD\",\"defaultComposerCredit\":\"Trad.\","
                                + "\"tuneType\":\"リール\",\"defaultKey\":\"D\",\"defaultTempo\":110}")
                .when().post("/api/v1/tunes").then().statusCode(201).body("tuneKind", equalTo("TRAD"))
                .body("title", equalTo("E2Eテストチューン")).extract().path("tuneId");

        given().when().get("/api/v1/tunes/" + tuneId).then().statusCode(200).body("tuneId", equalTo(tuneId))
                .body("title", equalTo("E2Eテストチューン")).body("defaultComposerCredit", equalTo("Trad."))
                .body("tuneType", equalTo("リール")).body("defaultKey", equalTo("D")).body("defaultTempo", equalTo(110));
    }

    @Test
    @DisplayName("存在しないIDは404 problem+jsonを返す")
    void getNotFound() {
        given().when().get("/api/v1/tunes/01234567-89ab-7def-0123-456789abcdef").then().statusCode(404)
                .contentType("application/problem+json").body("type", equalTo("urn:abservice:error:ENTITY_NOT_FOUND"))
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("タイトル空白は400 problem+json（検証エラー）を返す")
    void createValidationError() {
        given().contentType(ContentType.JSON).body("{\"title\":\"   \",\"tuneKind\":\"TRAD\"}").when()
                .post("/api/v1/tunes").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:VALIDATION_ERROR")).body("status", equalTo(400))
                .body("errors", not(empty())).body("errors[0].field", equalTo("tuneTitle"));
    }
}
