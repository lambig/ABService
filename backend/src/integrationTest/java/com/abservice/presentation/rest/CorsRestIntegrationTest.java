package com.abservice.presentation.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CORS 設定の統合テスト
 *
 * <p>
 * 本番は単一ドメイン・パスベースルーティングで同一オリジンになるためCORSは無効（既定値）で、開発・テストのみ有効にする。
 * ここで固定するのは「設定キーが現行Quarkusに認識され、許可オリジンだけが通る」こと。設定キーの誤りは起動時の
 * 警告に留まりCORSが無効なまま気付けないため、テストで実挙動を確認する。
 * </p>
 */
@QuarkusTest
@DisplayName("CORS 設定の統合テスト")
class CorsRestIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Test
    @DisplayName("許可オリジンからのプリフライトは許可ヘッダを返す")
    void preflightFromAllowedOriginIsAllowed() {
        given().header("Origin", ALLOWED_ORIGIN).header("Access-Control-Request-Method", "GET").when()
                .options("/api/v1/albums").then().statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo(ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("許可外オリジンには許可ヘッダを返さない")
    void preflightFromDisallowedOriginIsNotAllowed() {
        given().header("Origin", "http://disallowed.example.com").header("Access-Control-Request-Method", "GET").when()
                .options("/api/v1/albums").then().header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    @DisplayName("資格情報は許可しない（Cookieを使わずAuthorizationヘッダのみで認証するため）")
    void credentialsAreNotAllowed() {
        given().header("Origin", ALLOWED_ORIGIN).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization").when().options("/api/v1/albums").then()
                .statusCode(200).header("Access-Control-Allow-Credentials", equalTo("false"))
                .header("Access-Control-Allow-Headers", equalTo("Content-Type,Authorization"));
    }
}
