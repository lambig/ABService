package com.abservice.presentation.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ヘルスチェック・メトリクス・未捕捉例外の統合テスト
 *
 * <p>
 * readiness にDB接続確認が含まれること（アプリ側に HealthCheck 実装を持たず datasource
 * 拡張の自動登録に委ねる方針の担保）、 メトリクスが Prometheus 形式で公開されること、専用マッパーの無い例外も
 * {@code application/problem+json} で返ることを固定する。
 * </p>
 */
@QuarkusTest
@DisplayName("観測性エンドポイントの統合テスト")
class ObservabilityRestIntegrationTest {

    @Test
    @DisplayName("readinessはDB接続確認を含んでUPを返す")
    void readinessIncludesDatabaseCheck() {
        given().when().get("/q/health/ready").then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem(containsString("Database")));
    }

    @Test
    @DisplayName("livenessはUPを返す")
    void livenessIsUp() {
        given().when().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("メトリクスはPrometheus形式でHTTPサーバのリクエスト数を含む")
    void metricsAreExposedInPrometheusFormat() {
        given().when().get("/q/metrics").then().statusCode(200)
                .body(containsString("http_server_requests_seconds"));
    }

    @Test
    @DisplayName("未定義パスは404のproblem+jsonで返る")
    void unknownPathReturnsProblemJson() {
        given().when().get("/api/v1/no-such-resource").then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:HTTP_404"));
    }
}
