package com.abservice.presentation.rest.audio;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@QuarkusTest
@TestProfile(PrivateAudioAdminRestIntegrationTest.Enabled.class)
@ExtendWith(CleanDatabase.class)
class ListeningPreparationRestIntegrationTest {
    private static final String PATH = "/api/v1/admin/listening-preparation";

    @Test
    @DisplayName("公開状態と確定音源を結合取得し、保存先や端末トークンを返さない")
    void joinsStatesAndDevicesWithoutExposingAudioOrCredentials() throws Exception {
        final var draft = album("Draft confirmed");
        final var published = album("Published confirmed");
        final var missing = album("Published without audio");
        associate(draft);
        associate(published);
        authorized().post("/api/v1/albums/" + published + "/publish").then().statusCode(200);
        authorized().post("/api/v1/albums/" + missing + "/publish").then().statusCode(200);
        authorized().contentType(ContentType.JSON).body(Map.of("label", "Synthetic device"))
                .post("/api/v1/admin/listening-devices").then().statusCode(201);
        authorized().get(PATH).then().statusCode(200)
                .body("items.size()", equalTo(3)).body("totalPages", equalTo(1)).body("focused", nullValue())
                .body("items.find { it.title == 'Draft confirmed' }.audioConfirmed", equalTo(true))
                .body("items.find { it.title == 'Draft confirmed' }.publishedAt", nullValue())
                .body("items.find { it.title == 'Published confirmed' }.audioConfirmed", equalTo(true))
                .body("items.find { it.title == 'Published confirmed' }.publishedAt", not(nullValue()))
                .body("items.find { it.title == 'Published without audio' }.audioConfirmed", equalTo(false))
                .body("items[0]", not(hasKey("audioId"))).body("items[0]", not(hasKey("storageKey")))
                .body("devices.size()", equalTo(1)).body("devices[0]", not(hasKey("token")));
    }

    @Test
    @DisplayName("50件のページ境界とページ外の指定作品を一要求で取得する")
    void boundsPagesAndIncludesAnOffPageFocus() {
        final var ids = IntStream.range(0, 51).mapToObj(index -> album("Synthetic " + index))
                .sorted(Comparator.reverseOrder()).toList();
        final var first = authorized().queryParam("albumId", ids.getLast()).get(PATH).then().statusCode(200)
                .body("items.size()", equalTo(50)).body("totalPages", equalTo(2)).body("page", equalTo(0))
                .body("focused.albumId", equalTo(ids.getLast())).extract().jsonPath().<String>getList("items.albumId");
        assertThat(first).containsExactlyElementsOf(ids.subList(0, 50));
        authorized().queryParam("page", 1).get(PATH).then().statusCode(200)
                .body("items.size()", equalTo(1)).body("items[0].albumId", equalTo(ids.getLast()));
        authorized().queryParam("page", 2).get(PATH).then().statusCode(200).body("items.size()", equalTo(0));
    }

    @Test
    @DisplayName("認証・ページ範囲・指定作品を検証し、空一覧を正常に返す")
    void validatesAuthPageAndFocusedAlbum() {
        given().get(PATH).then().statusCode(401);
        given().header("Authorization", "Bearer deliberately-invalid-test-credential").get(PATH).then().statusCode(401);
        authorized().get(PATH).then().statusCode(200).body("items.size()", equalTo(0)).body("totalPages", equalTo(1));
        authorized().queryParam("page", -1).get(PATH).then().statusCode(400);
        authorized().queryParam("page", Integer.MAX_VALUE).get(PATH).then().statusCode(400);
        authorized().queryParam("albumId", "invalid").get(PATH).then().statusCode(400);
        authorized().queryParam("albumId", UUID.randomUUID()).get(PATH).then().statusCode(404);
    }

    private static String album(String title) {
        return authorized().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "title",
                                title,
                                "releaseDate",
                                "2026-01-01",
                                "artistDisplayName",
                                "Fixture"))
                .post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
    }

    private static void associate(String album) throws Exception {
        final String audio = authorized().post("/api/v1/admin/private-audio/registrations")
                .then().statusCode(201).extract().path("audioId");
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
            statement.setObject(2, UUID.fromString(audio));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        authorized().contentType(ContentType.JSON).body(
                Map.of(
                        "audioId",
                        audio,
                        "expectedRevision",
                        0))
                .put("/api/v1/admin/albums/" + album + "/listening-audio/crossfade").then().statusCode(200);
    }
}
