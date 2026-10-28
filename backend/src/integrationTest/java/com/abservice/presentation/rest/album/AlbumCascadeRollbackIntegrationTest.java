package com.abservice.presentation.rest.album;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 実際の記事保存をDB制約で失敗させ、別接続からcommit済み状態を確認する。 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("作品の連動操作で記事保存が失敗した場合のrollback")
class AlbumCascadeRollbackIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"unpublish", "delete"})
    @DisplayName("記事保存失敗で作品・記事・参照・世代が戻り、原因除去後は再実行できる")
    void failedArticleSaveRollsBackWholeOperation(String operation) throws SQLException {
        final var albumId = createPublishedAlbum();
        final var articleId = createPublishedArticle(albumId);
        final var albumBefore = albumState(albumId);
        final var articleBefore = articleState(articleId);
        final var generationBefore = generation();

        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE article ADD CONSTRAINT test_w04_reject_unpublish CHECK (is_public) NOT VALID");
            try {
                assertThat(execute(operation, albumId)).isEqualTo(500);
                assertThat(albumState(albumId)).isEqualTo(albumBefore);
                assertThat(articleState(articleId)).isEqualTo(articleBefore);
                assertThat(generation()).isEqualTo(generationBefore);
            } finally {
                statement.execute("ALTER TABLE article DROP CONSTRAINT test_w04_reject_unpublish");
            }
        }

        assertThat(execute(operation, albumId)).isEqualTo(200);
        assertThat(articleState(articleId).get("publicFlag")).isEqualTo(false);
        assertThat(generation()).isNotEqualTo(generationBefore);
    }

    private static int execute(String operation, String albumId) {
        return operation.equals("delete")
                ? authorized().delete("/api/v1/albums/" + albumId).statusCode()
                : authorized().post("/api/v1/albums/" + albumId + "/unpublish").statusCode();
    }

    private static Map<String, Object> albumState(String albumId) {
        return authorized().get("/api/v1/admin/albums/" + albumId).then().statusCode(200).extract().jsonPath()
                .getMap("$");
    }

    private static Map<String, Object> articleState(String articleId) {
        return authorized().get("/api/v1/admin/articles/" + articleId).then().statusCode(200).extract().jsonPath()
                .getMap("$");
    }

    private static String generation() {
        return given().get("/api/v1/public-data-generation").then().statusCode(200).extract().path("generation");
    }

    private static String createPublishedAlbum() {
        final String id = authorized().contentType(ContentType.JSON).body("""
                {"title":"Rollback fixture","releaseDate":"2026-01-01","artistDisplayName":"Fixture"}
                """).post("/api/v1/albums").then().statusCode(201).extract().path("albumId");
        authorized().post("/api/v1/albums/" + id + "/publish").then().statusCode(200);
        return id;
    }

    private static String createPublishedArticle(String albumId) {
        final String id = authorized().contentType(ContentType.JSON).body("""
                {"articleType":"ALBUM","title":"Rollback fixture","body":"fixture","bodyFormat":"MARKDOWN"}
                """).post("/api/v1/articles").then().statusCode(201).extract().path("articleId");
        authorized().contentType(ContentType.JSON)
                .body("{\"albumId\":\"%s\",\"expectedRevision\":0}".formatted(albumId))
                .put("/api/v1/articles/" + id + "/album").then().statusCode(200);
        authorized().post("/api/v1/articles/" + id + "/publish").then().statusCode(200);
        return id;
    }

    private static Connection connection() throws SQLException {
        final var config = ConfigProvider.getConfig();
        return DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
    }
}
