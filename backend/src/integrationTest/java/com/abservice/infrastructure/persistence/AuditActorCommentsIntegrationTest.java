package com.abservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.test.CleanDatabase;
import io.quarkus.test.junit.QuarkusTest;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** 現存する全actor予約列のDBコメントを検証する。 */
@QuarkusTest
@ExtendWith(CleanDatabase.class)
class AuditActorCommentsIntegrationTest {

    private static final String AUDIT_COMMENTS = """
            WITH expected(column_name, description) AS (
              VALUES
                ('created_by_service', '作成actorのサービス予約列（現状は常に未特定＝NULL）'),
                ('updated_by_service', '更新actorのサービス予約列（現状は常に未特定＝NULL）'),
                ('created_by_user', '作成actorのユーザー予約列（現状は常に未特定＝NULL）'),
                ('updated_by_user', '更新actorのユーザー予約列（現状は常に未特定＝NULL）')
            )
            SELECT count(*) AS total,
              count(*) FILTER (WHERE col_description(c.oid, a.attnum)
                IS DISTINCT FROM e.description) AS mismatches
            FROM pg_attribute a
            JOIN pg_class c ON c.oid = a.attrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN expected e ON e.column_name = a.attname
            WHERE n.nspname = 'public' AND c.relkind = 'r'
              AND a.attnum > 0 AND NOT a.attisdropped
            """;

    @Test
    @DisplayName("全10テーブルのactor予約列が未特定NULL契約を説明する")
    void allActorColumnsDocumentReservedNullContract() throws SQLException {
        final var config = ConfigProvider.getConfig();
        try (var connection = DriverManager.getConnection(
                config.getValue("quarkus.datasource.jdbc.url", String.class),
                config.getValue("quarkus.datasource.username", String.class),
                config.getValue("quarkus.datasource.password", String.class));
                var statement = connection.createStatement();
                var rows = statement.executeQuery(AUDIT_COMMENTS)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("total")).isEqualTo(40L);
            assertThat(rows.getLong("mismatches")).isZero();
        }
    }
}
