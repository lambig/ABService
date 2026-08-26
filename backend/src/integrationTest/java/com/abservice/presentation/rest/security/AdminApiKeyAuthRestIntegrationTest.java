package com.abservice.presentation.rest.security;

import static com.abservice.presentation.rest.AdminAuth.adminApiKey;
import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 管理者APIキー認証の E2E 統合テスト
 *
 * <p>
 * 管理操作（Command系・管理向けQuery・マスタ系Query）が認証を要求し、公開向けQueryが認証不要のままであることを、
 * 実際のHTTPリクエストで確認する。401 応答は RFC 9457 Problem Details と {@code WWW-Authenticate}
 * を伴う。
 * </p>
 */
@QuarkusTest
@DisplayName("管理者APIキー認証の統合テスト")
class AdminApiKeyAuthRestIntegrationTest {

    private static final String CREATE_ALBUM_BODY = "{\"title\":\"認証テストアルバム\",\"releaseDate\":\"2026-01-01\","
            + "\"artistDisplayName\":\"テストアーティスト\"}";

    @Test
    @DisplayName("APIキー無しのCommandは401 problem+jsonとWWW-Authenticateを返す")
    void commandWithoutApiKeyIsUnauthorized() {
        given().contentType(ContentType.JSON).body(CREATE_ALBUM_BODY).when().post("/api/v1/albums").then()
                .statusCode(401).contentType("application/problem+json")
                .header("WWW-Authenticate", containsString("Bearer"))
                .body("type", equalTo("urn:abservice:error:UNAUTHORIZED")).body("status", equalTo(401));
    }

    @Test
    @DisplayName("誤ったAPIキーのCommandは401を返す")
    void commandWithWrongApiKeyIsUnauthorized() {
        given().header("Authorization", "Bearer " + adminApiKey() + "-wrong").contentType(ContentType.JSON)
                .body(CREATE_ALBUM_BODY).when().post("/api/v1/albums").then().statusCode(401)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Bearer以外のスキームのCommandは401を返す")
    void commandWithNonBearerSchemeIsUnauthorized() {
        given().header("Authorization", "Basic " + adminApiKey()).contentType(ContentType.JSON)
                .body(CREATE_ALBUM_BODY).when().post("/api/v1/albums").then().statusCode(401);
    }

    @Test
    @DisplayName("正しいAPIキーのCommandは成功する")
    void commandWithApiKeySucceeds() {
        authorized().contentType(ContentType.JSON).body(CREATE_ALBUM_BODY).when().post("/api/v1/albums").then()
                .statusCode(201);
    }

    @Test
    @DisplayName("公開向けQueryはAPIキー無しでも成功する")
    void publicQueryNeedsNoApiKey() {
        given().when().get("/api/v1/albums").then().statusCode(200);
        given().when().get("/api/v1/articles").then().statusCode(200);
    }

    @Test
    @DisplayName("管理向けQueryはAPIキー無しでは401を返す")
    void adminQueryWithoutApiKeyIsUnauthorized() {
        given().when().get("/api/v1/admin/albums").then().statusCode(401);
        given().when().get("/api/v1/admin/articles").then().statusCode(401);
    }

    @Test
    @DisplayName("マスタ系QueryはAPIキー無しでは401を返す")
    void masterDataQueryWithoutApiKeyIsUnauthorized() {
        given().when().get("/api/v1/tunes").then().statusCode(401);
    }

    @Test
    @DisplayName("マスタ系QueryはAPIキーがあれば成功する")
    void masterDataQueryWithApiKeySucceeds() {
        authorized().when().get("/api/v1/tunes").then().statusCode(200);
    }
}
