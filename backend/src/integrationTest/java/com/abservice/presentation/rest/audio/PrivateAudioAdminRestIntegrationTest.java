package com.abservice.presentation.rest.audio;

import io.restassured.response.Response;
import java.util.List;
import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@QuarkusTest
@TestProfile(PrivateAudioAdminRestIntegrationTest.Enabled.class)
@ExtendWith(CleanDatabase.class)
class PrivateAudioAdminRestIntegrationTest {
    private static final String REGISTRATIONS = "/api/v1/admin/private-audio/registrations";

    public static class Enabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "abservice.private-audio.enabled",
                    "true",
                    "abservice.private-audio.bucket",
                    "synthetic-private-audio-admin",
                    "abservice.private-audio.temporary-directory",
                    "/tmp/abservice-private-audio-admin-test",
                    "abservice.private-audio.maintenance-interval",
                    "PT1H");
        }
    }

    @Test
    @DisplayName("予約はサーバー発行ID・期限・Locationを返し同じ状態を管理照会できる")
    void reservesAndReadsWithoutStorageLocation() {
        final var created = authorized().post(REGISTRATIONS).then().statusCode(201).extract();
        final String id = created.path("audioId");
        assertThat(UUID.fromString(id).version()).isEqualTo(7);
        assertThat(created.header("Location")).isEqualTo(REGISTRATIONS + "/" + id);
        final String expires = created.path("expiresAt");
        final String createdAt = created.path("createdAt");
        assertThat(Instant.parse(expires)).isAfter(Instant.parse(createdAt)).isBefore(Instant.now().plusSeconds(901));
        authorized().get(REGISTRATIONS + "/" + id).then().statusCode(200)
                .body("audioId", equalTo(id)).body("state", equalTo("PENDING")).body("metadata", nullValue())
                .body("$", not(hasKey("url"))).body("$", not(hasKey("storageKey")));
    }

    @Test
    @DisplayName("匿名要求と不正な資格情報は全管理入口で401になる")
    void rejectsAnonymousAndInvalidCredentials() {
        final var id = UUID.randomUUID().toString();
        given().post(REGISTRATIONS).then().statusCode(401);
        given().get(REGISTRATIONS + "/" + id).then().statusCode(401);
        given().get(crossfade(id)).then().statusCode(401);
        given().contentType(ContentType.JSON).body(
                Map.of(
                        "audioId",
                        id,
                        "expectedRevision",
                        0))
                .put(crossfade(id)).then().statusCode(401);
        given().header("Authorization", "Bearer deliberately-invalid-test-credential")
                .post(REGISTRATIONS).then().statusCode(401);
    }

    @Test
    @DisplayName("未存在・不正ID・未指定世代・未確定音源を拒否する")
    void rejectsInvalidReferencesAndPendingAudio() {
        final var album = album();
        final var audio = reserve();
        authorized().contentType(ContentType.JSON).body("{}").put(crossfade(album)).then().statusCode(400);
        authorized().contentType(ContentType.JSON).put(crossfade(album)).then().statusCode(400);
        authorized().get(REGISTRATIONS + "/not-a-uuid").then().statusCode(400);
        authorized().get(REGISTRATIONS + "/" + UUID.randomUUID()).then().statusCode(404);
        authorized().get(crossfade(UUID.randomUUID().toString())).then().statusCode(404);
        authorized().contentType(ContentType.JSON).body(Map.of("audioId", audio))
                .put(crossfade(album)).then().statusCode(400);
        authorized().contentType(ContentType.JSON).body(
                Map.of(
                        "audioId",
                        audio,
                        "expectedRevision",
                        -1))
                .put(crossfade(album)).then().statusCode(400);
        set(
                album,
                audio,
                0).then().statusCode(409);
        set(
                album,
                UUID.randomUUID().toString(),
                0).then().statusCode(404);
        set(
                UUID.randomUUID().toString(),
                audio,
                0).then().statusCode(404);
        authorized().get(crossfade(album)).then().statusCode(200)
                .body("audioId", nullValue()).body("revision", equalTo(0)).body("kind", equalTo("album-crossfade"));
    }

    @Test
    @DisplayName("確定音源の関連付けは専用世代を進め古い更新を拒否し公開Albumを変えない")
    void associatesConfirmedAudioWithRevision() throws Exception {
        final var album = album();
        final var first = confirmed();
        final var second = confirmed();
        authorized().post("/api/v1/albums/" + album + "/publish").then().statusCode(200);
        final Object generation = given().get("/api/v1/public-data-generation").then()
                .statusCode(200).extract().path("generation");
        final Object revision = authorized().get("/api/v1/admin/albums/" + album).then()
                .statusCode(200).extract().path("revision");
        set(
                album,
                first,
                0).then().statusCode(200).body("revision", equalTo(1));
        set(
                album,
                second,
                0).then().statusCode(409);
        set(
                album,
                second,
                1).then().statusCode(200).body("revision", equalTo(2));
        authorized().get(crossfade(album)).then().statusCode(200)
                .body("audioId", equalTo(second)).body("revision", equalTo(2));
        authorized().get("/api/v1/admin/albums/" + album).then().statusCode(200)
                .body("revision", equalTo(revision)).body("$", not(hasKey("audioId")));
        given().get("/api/v1/albums/" + album).then().statusCode(200).body("$", not(hasKey("audioId")));
        given().get("/api/v1/public-data-generation").then().statusCode(200).body("generation", equalTo(generation));
        authorized().get(REGISTRATIONS + "/" + first).then().statusCode(200)
                .body("state", equalTo("CONFIRMED")).body("metadata.byteLength", equalTo(100))
                .body("metadata.sha256", equalTo("a".repeat(64)));
        given().get("/api/v1/private-audio/registrations/" + first).then().statusCode(404);
        given().get("/api/v1/albums/" + album + "/listening-audio/crossfade").then().statusCode(404);
        authorized().contentType(ContentType.JSON).body(
                Map.of(
                        "audioId",
                        first,
                        "expectedRevision",
                        0))
                .put("/api/v1/admin/albums/" + album + "/listening-audio/track").then().statusCode(404);
    }

    @Test
    @DisplayName("同じ未設定世代から競合した二要求は一つだけ成功する")
    void concurrentAssignmentsHaveOneWinner() throws Exception {
        final var album = album();
        final var first = confirmed();
        final var second = confirmed();
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var one = CompletableFuture.supplyAsync(
                    () -> set(
                            album,
                            first,
                            0).statusCode(),
                    executor);
            final var two = CompletableFuture.supplyAsync(
                    () -> set(
                            album,
                            second,
                            0).statusCode(),
                    executor);
            assertThat(List.of(one.join(), two.join())).containsExactlyInAnyOrder(200, 409);
        }
        authorized().get(crossfade(album)).then().statusCode(200).body("revision", equalTo(1));
    }

    @Test
    @DisplayName("Album削除は選択だけを除去し確定音源を削除しない")
    void albumDeletionKeepsRegistration() throws Exception {
        final var album = album();
        final var audio = confirmed();
        set(
                album,
                audio,
                0).then().statusCode(200);
        authorized().delete("/api/v1/albums/" + album).then().statusCode(200);
        authorized().get(crossfade(album)).then().statusCode(404);
        authorized().get(REGISTRATIONS + "/" + audio).then().statusCode(200).body("state", equalTo("CONFIRMED"));
    }

    private static Response set(
            String album,
            String audio,
            int revision) {
        return authorized().contentType(ContentType.JSON).body(
                Map.of(
                        "audioId",
                        audio,
                        "expectedRevision",
                        revision))
                .put(crossfade(album));
    }

    private static String crossfade(String album) {
        return "/api/v1/admin/albums/" + album + "/listening-audio/crossfade";
    }

    private static String reserve() {
        return authorized().post(REGISTRATIONS).then().statusCode(201).extract().path("audioId");
    }

    private static String album() {
        return authorized().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "title",
                                "Synthetic listening album",
                                "releaseDate",
                                "2026-01-01",
                                "artistDisplayName",
                                "Fixture"))
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    /** FLAC実体の検査は基盤の統合試験で扱い、本試験は確定後のAPI契約を検査する。 */
    private static String confirmed() throws Exception {
        final var id = reserve();
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.prepareStatement("""
                        UPDATE private_audio_registration SET state = 'CONFIRMED', byte_length = 100,
                            sha256 = ?, sample_rate = 44100, channels = 2, bits_per_sample = 16, total_samples = 44100
                        WHERE audio_id = ?
                        """)) {
            statement.setString(1, "a".repeat(64));
            statement.setObject(2, UUID.fromString(id));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        return id;
    }
}
