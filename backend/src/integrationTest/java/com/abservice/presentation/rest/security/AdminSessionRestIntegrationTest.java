package com.abservice.presentation.rest.security;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("管理セッション交換・失効のHTTP契約")
class AdminSessionRestIntegrationTest {

    @Test
    @DisplayName("交換にはAPIキーが必要で無認証・誤ったキーは401になる")
    void exchangeRequiresValidKey() {
        given().when().post("/api/v1/admin/sessions").then().statusCode(401)
                .contentType("application/problem+json");
        given().header("Authorization", "Bearer wrong-key").when().post("/api/v1/admin/sessions")
                .then().statusCode(401).body("type", equalTo("urn:abservice:error:UNAUTHORIZED"));
    }

    @Test
    @DisplayName("交換はno-storeと期限を返しトークンで管理照会とCommandを実行できる")
    void sessionAuthenticatesQueriesAndCommands() {
        final String token = authorized().when().post("/api/v1/admin/sessions").then().statusCode(200)
                .contentType(ContentType.JSON).header("Cache-Control", "no-store")
                .header("Pragma", "no-cache").body("expiresAt", notNullValue()).extract().path("token");

        given().header("Authorization", "Bearer " + token).when().get("/api/v1/admin/albums")
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{\"title\":\"セッション作成\",\"releaseDate\":\"2026-01-01\",\"artistDisplayName\":\"管理者\"}")
                .when().post("/api/v1/albums").then().statusCode(201);
    }

    @Test
    @DisplayName("セッションからの再交換は403 Problem Detailsとなる")
    void sessionCannotExchangeAgain() {
        final String token = authorized().when().post("/api/v1/admin/sessions").then().statusCode(200)
                .extract().path("token");

        given().header("Authorization", "Bearer " + token).when().post("/api/v1/admin/sessions")
                .then().statusCode(403).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:FORBIDDEN"));
    }

    @Test
    @DisplayName("破棄後のトークンは照会も再破棄も401となり別セッションは残る")
    void revokedTokenCannotBeReused() {
        final String first = authorized().when().post("/api/v1/admin/sessions").then().statusCode(200)
                .extract().path("token");
        final String second = authorized().when().post("/api/v1/admin/sessions").then().statusCode(200)
                .extract().path("token");

        given().header("Authorization", "Bearer " + first).when().delete("/api/v1/admin/sessions/current")
                .then().statusCode(204).header("Cache-Control", "no-store");
        given().header("Authorization", "Bearer " + first).when().get("/api/v1/admin/albums")
                .then().statusCode(401).contentType("application/problem+json");
        given().header("Authorization", "Bearer " + first).when().delete("/api/v1/admin/sessions/current")
                .then().statusCode(401);
        given().header("Authorization", "Bearer " + second).when().get("/api/v1/admin/albums")
                .then().statusCode(200);
    }

    @Test
    @DisplayName("APIキーは直接管理操作に使えるが自分のセッションの破棄には使えない")
    void machineKeyRemainsCompatible() {
        authorized().when().get("/api/v1/admin/albums").then().statusCode(200);
        authorized().when().delete("/api/v1/admin/sessions/current").then().statusCode(403);
        given().when().get("/api/v1/albums").then().statusCode(200);
    }

    @Test
    @DisplayName("OpenAPIにも交換応答と破棄経路を公開する")
    void openApiIncludesSessionContract() {
        final String schema = given().accept(ContentType.JSON).when().get("/q/openapi").then().statusCode(200)
                .extract().asString();

        assertThat(schema).contains(
                "/api/v1/admin/sessions",
                "/api/v1/admin/sessions/current",
                "AdminSessionResponse");
    }
}
