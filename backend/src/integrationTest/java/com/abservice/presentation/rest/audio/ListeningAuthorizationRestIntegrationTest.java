package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@QuarkusTest
@TestProfile(ListeningDeviceRestIntegrationTest.Enabled.class)
@ExtendWith(CleanDatabase.class)
@DisplayName("別端末の短い接続コードによる許可と一度限りの受取")
class ListeningAuthorizationRestIntegrationTest {
    private static final String START = "/api/v1/listening/authorizations";
    private static final String ADMIN = "/api/v1/admin/listening-authorizations";

    @BeforeEach
    void resetBudget() throws SQLException {
        sql("INSERT INTO listening_authorization_budget(singleton) VALUES (1) ON CONFLICT DO NOTHING");
    }

    @Test
    @DisplayName("匿名で開始し管理者が許可した要求だけがlistener資格情報を一度受け取る")
    void issuesOnlyToProofOwnerAfterApproval() {
        final var started = start();
        assertThat(started.<String>path("code")).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(started.header("Cache-Control")).isEqualTo("no-store");
        final var resolved = authorized().contentType(ContentType.JSON)
                .body(Map.of("code", started.<String>path("code")))
                .post(ADMIN + "/resolve").then().statusCode(200).body("state", equalTo("PENDING"))
                .body("proof", nullValue()).body("token", nullValue()).extract();
        assertThat(resolved.<String>path("requestId")).isEqualTo(started.path("requestId"));
        given().contentType(ContentType.JSON).body(proof(started)).post(START + "/token").then()
                .body("state", equalTo("PENDING")).body("token", nullValue());
        approve(started);
        rewindPoll(started);
        final var issued = given().contentType(ContentType.JSON).body(proof(started)).post(START + "/token").then()
                .statusCode(200).header("Cache-Control", "no-store").body("state", equalTo("ISSUED")).extract();
        final String token = issued.path("token");
        assertThat(token).matches("abs_device_[0-9a-f]{64}");
        given().contentType(ContentType.JSON).body(proof(started)).post(START + "/token").then()
                .body("state", equalTo("CONSUMED")).body("token", nullValue())
                .body("deviceId", equalTo(started.path("requestId")));
        given().header("Authorization", "Bearer " + token).get("/api/v1/admin/listening-devices").then()
                .statusCode(403);
        authorized().get("/api/v1/admin/listening-devices").then().body("devices.size()", equalTo(1));
        authorized().delete("/api/v1/admin/listening-devices/" + started.<String>path("requestId")).then()
                .statusCode(204);
        given().header("Authorization", "Bearer " + token).get("/api/v1/listening/package").then().statusCode(401);
    }

    @Test
    @DisplayName("短いコード・誤proofでは受取できず匿名の許可・照合も拒否する")
    void protectsBothAuthorizationBoundaries() {
        final var started = start();
        authorized().contentType(ContentType.JSON).body(Map.of())
                .post(ADMIN + "/" + started.<String>path("requestId") + "/decision").then().statusCode(400);
        authorized().contentType(ContentType.JSON).body(
                Map.of(
                        "approve",
                        true,
                        "validDays",
                        91))
                .post(ADMIN + "/" + started.<String>path("requestId") + "/decision").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("code", started.<String>path("code")))
                .post(ADMIN + "/resolve").then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("approve", true))
                .post(ADMIN + "/" + started.<String>path("requestId") + "/decision").then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "requestId",
                                started.<String>path("requestId"),
                                "proof",
                                "0".repeat(64)))
                .post(START + "/token").then().body("state", equalTo("NOT_FOUND")).body("token", nullValue());
        given().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "requestId",
                                started.<String>path("requestId"),
                                "proof",
                                started.<String>path("code")))
                .post(START + "/token").then().statusCode(400);
    }

    @Test
    @DisplayName("拒否と期限切れの要求から資格情報は発行されない")
    void deniesExpiredOrRejectedRequests() throws SQLException {
        final var denied = start();
        authorized().contentType(ContentType.JSON).body(Map.of("approve", false))
                .post(ADMIN + "/" + denied.<String>path("requestId") + "/decision");
        given().contentType(ContentType.JSON).body(proof(denied)).post(START + "/token").then()
                .body("state", equalTo("DENIED")).body("token", nullValue());
        final var expired = start();
        sql("UPDATE listening_authorization SET expires_at = clock_timestamp() - interval '1 second'");
        approve(expired);
        given().contentType(ContentType.JSON).body(proof(expired)).post(START + "/token").then()
                .body("state", equalTo("EXPIRED")).body("token", nullValue());
    }

    @Test
    @DisplayName("同時受取でも資格情報は一件だけ発行し再発行しない")
    void serializesConcurrentRedemption() {
        final var started = start();
        approve(started);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var jobs = Stream
                    .generate(
                            () -> CompletableFuture.supplyAsync(
                                    () -> given().contentType(ContentType.JSON).body(proof(started))
                                            .post(START + "/token")
                                            .then().statusCode(200).extract().<String>path("state"),
                                    executor))
                    .limit(2)
                    .toList();
            assertThat(jobs.stream().map(CompletableFuture::join).toList())
                    .containsExactlyInAnyOrder("ISSUED", "CONSUMED");
        }
        authorized().get("/api/v1/admin/listening-devices").then().body("devices.size()", equalTo(1));
    }

    @Test
    @DisplayName("開始と誤コード照合の回数制限が失敗応答後も保持される")
    void enforcesCommittedBudgets() {
        IntStream.range(0, 30).forEach(index -> start());
        given().contentType(ContentType.JSON).body(Map.of("label", "synthetic device")).post(START).then()
                .statusCode(429).body("proof", nullValue());
        IntStream.range(0, 20).forEach(
                index -> authorized().contentType(ContentType.JSON).body(Map.of("code", "WRONG" + index))
                        .post(ADMIN + "/resolve").then().statusCode(200).body("state", equalTo("NOT_FOUND")));
        authorized().contentType(ContentType.JSON).body(Map.of("code", "WRONG")).post(ADMIN + "/resolve").then()
                .statusCode(429);
    }

    @Test
    @DisplayName("早すぎるpollは資格情報を発行せず再試行間隔を返す")
    void throttlesPolling() {
        final var started = start();
        given().contentType(ContentType.JSON).body(proof(started)).post(START + "/token").then().statusCode(200);
        given().contentType(ContentType.JSON).body(proof(started)).post(START + "/token").then().statusCode(429)
                .header("Retry-After", "5").body("token", nullValue());
    }

    private static ExtractableResponse<Response> start() {
        return given().contentType(ContentType.JSON).body(Map.of("label", "synthetic device"))
                .post(START).then().statusCode(200).body("state", equalTo("PENDING")).extract();
    }

    private static Map<String, String> proof(ExtractableResponse<Response> started) {
        return Map.of(
                "requestId",
                started.path("requestId"),
                "proof",
                started.path("proof"));
    }

    private static void approve(ExtractableResponse<Response> started) {
        authorized().contentType(ContentType.JSON).body(
                Map.of(
                        "approve",
                        true,
                        "validDays",
                        7))
                .post(ADMIN + "/" + started.<String>path("requestId") + "/decision").then().statusCode(200);
    }

    private static void rewindPoll(ExtractableResponse<Response> started) {
        try {
            sql(
                    "UPDATE listening_authorization SET last_poll_at = NULL WHERE request_id = '"
                            + started.<String>path("requestId") + "'");
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void sql(String statement) throws SQLException {
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var query = connection.createStatement()) {
            query.execute(statement);
        }
    }
}
