package com.abservice.test;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * テストのたびにデータベースを空へ戻す JUnit 拡張
 *
 * <p>
 * Flyway の clean-at-start は Quarkus
 * アプリの起動時に1回しか走らないため、同じ実行の中ではテストクラス同士がデータを共有する。共有したままでは
 * 「一覧が指定した順に並ぶ」ことを全体では確かめられず、自分が作った数件の相対順しか見られない（#252）。各テストの前に空へ戻し、母集団をそのテストが作った
 * ものだけにする。
 * </p>
 *
 * <p>
 * 登録はテストクラスの {@code @ExtendWith} で行う。JUnit
 * の自動検出（サービス定義）は使えない。自動検出された拡張はシステムのクラスローダーで動き、 そこから設定を引くと SmallRye Config
 * のサービス解決が Quarkus の実装を見つけられずに落ちる。Quarkus のテストコールバックも
 * {@code src/integrationTest} のリソースからは拾われない。
 * </p>
 */
public final class CleanDatabase implements BeforeEachCallback {

    /**
     * 移行履歴（{@code flyway_schema_history}）を残して public スキーマの全テーブルを空にする。
     *
     * <p>
     * 対象が1つも無いときも同じ1文で済むよう、文の組み立てを DO ブロックの中へ置く。
     * </p>
     */
    private static final String TRUNCATE_ALL = """
            DO $$
            BEGIN
              EXECUTE (
                SELECT coalesce(
                  'TRUNCATE TABLE '
                    || string_agg(format('%I.%I', schemaname, tablename), ', ')
                    || ' RESTART IDENTITY CASCADE',
                  'SELECT 1'
                )
                FROM pg_tables
                WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
              );
            END $$;
            """;

    @Override
    public void beforeEach(ExtensionContext context) throws SQLException {
        try (var connection = DriverManager.getConnection(
                url(),
                username(),
                password());
                var statement = connection.createStatement()) {
            statement.execute(TRUNCATE_ALL);
        }
    }

    private static String url() {
        return value("quarkus.datasource.jdbc.url");
    }

    private static String username() {
        return value("quarkus.datasource.username");
    }

    private static String password() {
        return value("quarkus.datasource.password");
    }

    private static String value(String key) {
        return ConfigProvider.getConfig().getValue(key, String.class);
    }
}
