package com.abservice.presentation.rest.publication;

import static com.abservice.presentation.rest.AdminAuth.authorized;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 保存・取消・並行commitと公開Queryの世代を実DBで照合する。 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
@DisplayName("公開データ世代の統合テスト")
class PublicDataGenerationIntegrationTest {

    @Test
    @DisplayName("匿名照会は保存禁止の不透明トークンだけを返し、読取りでは変化しない")
    void anonymousReadIsStable() {
        final String initial = generation();
        assertThat(UUID.fromString(initial).toString()).isEqualTo(initial);
        given().get("/api/v1/albums").then().statusCode(200);
        assertThat(generation()).isEqualTo(initial);
    }

    @Test
    @DisplayName("サイト文言の保存と元の値への変更は別世代、検証失敗は同じ世代")
    void savedAndRejectedUpdates() {
        final String initial = generation();
        saveContent("first");
        final String first = generation();
        saveContent("second");
        final String second = generation();
        saveContent("first");
        assertThat(generation()).isNotIn(
                initial,
                first,
                second);
        final String beforeFailure = generation();
        authorized().contentType(ContentType.JSON).body("{}").put("/api/v1/site-contents/test.generation")
                .then().statusCode(400);
        assertThat(generation()).isEqualTo(beforeFailure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"album", "track", "track_tune", "tune", "album_external_audio", "article",
            "article_album_reference", "article_tag", "article_tag_link", "site_content"})
    @DisplayName("公開Queryの全参照表の更新・削除・TRUNCATEは世代を変更する")
    void allDependenciesAdvance(String table) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            final String initial = generation();
            statement.execute("UPDATE " + table + " SET version = version WHERE false");
            final String updated = generation();
            assertThat(updated).isNotEqualTo(initial);
            statement.execute("DELETE FROM " + table + " WHERE false");
            final String deleted = generation();
            assertThat(deleted).isNotIn(initial, updated);
            statement.execute("TRUNCATE " + table + " CASCADE");
            assertThat(generation()).isNotIn(
                    initial,
                    updated,
                    deleted);
        }
    }

    @Test
    @DisplayName("新しい業務テーブルの追加時に世代追跡の検討漏れを検出する")
    void dependencyCoverage() throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT count(*) FROM pg_tables t
                        WHERE schemaname = 'public'
                          AND tablename NOT IN ('flyway_schema_history', 'public_data_generation')
                          AND NOT EXISTS (
                            SELECT 1 FROM pg_trigger g
                            WHERE g.tgrelid = ('public.' || t.tablename)::regclass
                              AND g.tgname = 'public_data_changed' AND g.tgenabled = 'O'
                          )
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isZero();
        }
    }

    @Test
    @DisplayName("未commitの保存は見えず、rollbackではデータと世代がともに戻る")
    void transactionVisibilityAndRollback() throws SQLException {
        saveContent("original");
        final String initial = generation();
        try (var connection = connection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("UPDATE site_content SET content = 'uncommitted'");
            assertThat(generation()).isEqualTo(initial);
            connection.rollback();
            assertThat(generation()).isEqualTo(initial);
            statement.execute("UPDATE site_content SET content = 'committed'");
            connection.commit();
            assertThat(generation()).isNotEqualTo(initial);
        }
    }

    @Test
    @DisplayName("並行保存の後発commitも別世代となり、最初のcommitを上書きして取りこぼさない")
    void concurrentCommits() throws Exception {
        final String initial = generation();
        try (var first = connection();
                var statement = first.createStatement();
                var executor = Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            statement.execute("DELETE FROM album WHERE false");
            final var second = executor.submit(() -> {
                try (var next = connection(); var change = next.createStatement()) {
                    change.execute("DELETE FROM article WHERE false");
                    return generation();
                }
            });
            assertThat(generation()).isEqualTo(initial);
            final String firstGeneration;
            try (var rows = statement.executeQuery("SELECT generation::text FROM public_data_generation")) {
                assertThat(rows.next()).isTrue();
                firstGeneration = rows.getString(1);
            }
            first.commit();
            assertThat(second.get(10, TimeUnit.SECONDS)).isNotIn(initial, firstGeneration);
        }
    }

    private static String generation() {
        return given().get("/api/v1/public-data-generation").then().statusCode(200)
                .header("Cache-Control", "no-store").extract().path("generation");
    }

    private static void saveContent(String value) {
        authorized().contentType(ContentType.JSON)
                .body("{\"content\":\"" + value + "\",\"contentFormat\":\"PLAIN_TEXT\"}")
                .put("/api/v1/site-contents/test.generation").then().statusCode(200);
    }

    private static Connection connection() throws SQLException {
        final var config = ConfigProvider.getConfig();
        return DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
    }
}
