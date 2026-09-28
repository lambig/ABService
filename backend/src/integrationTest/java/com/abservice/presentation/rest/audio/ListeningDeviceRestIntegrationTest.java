package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@QuarkusTest
@TestProfile(ListeningDeviceRestIntegrationTest.Enabled.class)
@ExtendWith(CleanDatabase.class)
@DisplayName("試聴端末の資格情報のHTTP契約")
class ListeningDeviceRestIntegrationTest {
    private static final String DEVICES = "/api/v1/admin/listening-devices";
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    /**
     * 一時領域はJVMの一時ディレクトリ配下に取る。固定の {@code /tmp} は macOS では symlink 経由になり、
     * 実行入口が起動時に拒否するため、手元で検査できなくなる。
     */
    public static class Enabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "abservice.private-audio.enabled",
                    "true",
                    "abservice.private-audio.bucket",
                    "synthetic-listening-device",
                    "abservice.private-audio.temporary-directory",
                    System.getProperty("java.io.tmpdir") + "/abservice-listening-device-test",
                    "abservice.private-audio.maintenance-interval",
                    "PT1H");
        }
    }

    @Test
    @DisplayName("発行は201・Location・保存禁止ヘッダと一度だけのトークンを返し既定30日で失効する")
    void issuesTokenOnceWithDefaultValidity() {
        final var issued = issue(Map.of("label", "会場端末A"));
        final String id = issued.path("device.deviceId");
        assertThat(UUID.fromString(id).version()).isEqualTo(7);
        assertThat(issued.header("Location")).isEqualTo(DEVICES + "/" + id);
        assertThat(issued.header("Cache-Control")).isEqualTo("no-store");
        assertThat(issued.<String>path("token")).matches("^abs_device_[0-9a-f]{64}$");
        assertThat(issued.<String>path("device.state")).isEqualTo("ACTIVE");
        assertThat(issued.<String>path("device.label")).isEqualTo("会場端末A");
        assertThat(issued.<Object>path("device.revokedAt")).isNull();
        assertThat(Instant.parse(issued.path("device.expiresAt")))
                .isBetween(
                        Instant.now().plus(Duration.ofDays(30)).minus(TOLERANCE),
                        Instant.now().plus(Duration.ofDays(30)).plus(TOLERANCE));
        final var week = issue(withValidity(7));
        assertThat(Instant.parse(week.path("device.expiresAt")))
                .isBetween(
                        Instant.now().plus(Duration.ofDays(7)).minus(TOLERANCE),
                        Instant.now().plus(Duration.ofDays(7)).plus(TOLERANCE));
    }

    @Test
    @DisplayName("一覧は発行の新しい順でトークンを含まない")
    void listsWithoutTokens() {
        final String first = issue(Map.of("label", "一台目")).path("device.deviceId");
        final String second = issue(Map.of("label", "二台目")).path("device.deviceId");
        authorized().get(DEVICES).then().statusCode(200).body("devices", hasSize(2))
                .body("devices[0].deviceId", equalTo(second)).body("devices[1].deviceId", equalTo(first))
                .body("devices[0]", not(hasKey("token"))).body("devices[0]", not(hasKey("tokenDigest")))
                .body("devices[0].state", equalTo("ACTIVE"));
    }

    @Test
    @DisplayName("端末トークンは端末として認証され管理操作には使えず不正な形式や無認証は401になる")
    void deviceTokenAuthenticatesAsListenerOnly() {
        final String token = issue(Map.of("label", "端末")).path("token");
        asDevice(token).get(DEVICES).then().statusCode(403).contentType("application/problem+json")
                .body("type", equalTo("urn:abservice:error:FORBIDDEN"));
        asDevice(token).contentType(ContentType.JSON).body(Map.of("label", "x")).post(DEVICES).then()
                .statusCode(403);
        asDevice(token).get("/api/v1/admin/albums").then().statusCode(403);
        asDevice(token).post("/api/v1/admin/sessions").then().statusCode(403);
        asDevice("abs_device_" + "0".repeat(64)).get(DEVICES).then().statusCode(401)
                .body("type", equalTo("urn:abservice:error:UNAUTHORIZED"));
        given().get(DEVICES).then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("label", "x")).post(DEVICES).then().statusCode(401);
        given().delete(DEVICES + "/" + UUID.randomUUID()).then().statusCode(401);
        given().get("/api/v1/albums").then().statusCode(200);
    }

    @Test
    @DisplayName("失効は204で以後のトークンを401にし再失効はべき等で未存在は404になる")
    void revokesIdempotently() {
        final var issued = issue(Map.of("label", "失効対象"));
        final String id = issued.path("device.deviceId");
        final String token = issued.path("token");
        asDevice(token).get(DEVICES).then().statusCode(403);
        authorized().delete(DEVICES + "/" + id).then().statusCode(204);
        asDevice(token).get(DEVICES).then().statusCode(401);
        authorized().get(DEVICES).then().statusCode(200).body("devices[0].state", equalTo("REVOKED"))
                .body("devices[0].revokedAt", not(nullValue()));
        authorized().delete(DEVICES + "/" + id).then().statusCode(204);
        authorized().delete(DEVICES + "/" + UUID.randomUUID()).then().statusCode(404);
        authorized().delete(DEVICES + "/not-a-uuid").then().statusCode(400);
    }

    @Test
    @DisplayName("期限を過ぎたトークンは401になり一覧でEXPIREDと表示される")
    void expiredTokenIsRejected() throws Exception {
        final var issued = issue(Map.of("label", "期限切れ"));
        final String id = issued.path("device.deviceId");
        final String token = issued.path("token");
        expire(id);
        asDevice(token).get(DEVICES).then().statusCode(401);
        authorized().get(DEVICES).then().statusCode(200).body("devices[0].state", equalTo("EXPIRED"))
                .body("devices[0].revokedAt", nullValue());
    }

    @Test
    @DisplayName("表示名の欠落・超過と有効期間の範囲外は400になる")
    void rejectsInvalidInput() {
        authorized().contentType(ContentType.JSON).post(DEVICES).then().statusCode(400);
        authorized().contentType(ContentType.JSON).body("{}").post(DEVICES).then().statusCode(400);
        authorized().contentType(ContentType.JSON).body(Map.of("label", "  ")).post(DEVICES).then()
                .statusCode(400);
        authorized().contentType(ContentType.JSON).body(Map.of("label", "x".repeat(101))).post(DEVICES).then()
                .statusCode(400);
        authorized().contentType(ContentType.JSON).body(withValidity(0)).post(DEVICES).then().statusCode(400);
        authorized().contentType(ContentType.JSON).body(withValidity(91)).post(DEVICES).then().statusCode(400);
        authorized().get(DEVICES).then().statusCode(200).body("devices", hasSize(0));
    }

    private static ExtractableResponse<Response> issue(Map<String, Object> body) {
        return authorized().contentType(ContentType.JSON).body(body).post(DEVICES).then().statusCode(201).extract();
    }

    private static Map<String, Object> withValidity(int validDays) {
        return Map.of(
                "label",
                "有効期間つき",
                "validDays",
                validDays);
    }

    private static RequestSpecification asDevice(String token) {
        return given().header("Authorization", "Bearer " + token);
    }

    /** 期限はDB時計で判定するため、DB上の期限を過去へ動かして検査する。 */
    private static void expire(String id) throws Exception {
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.prepareStatement("""
                        UPDATE listening_device SET created_at = created_at - interval '1 hour',
                            expires_at = created_at - interval '59 minutes'
                        WHERE device_id = ?
                        """)) {
            statement.setObject(1, UUID.fromString(id));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }
}
