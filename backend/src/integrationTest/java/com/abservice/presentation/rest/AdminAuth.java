package com.abservice.presentation.rest;

import static io.restassured.RestAssured.given;

import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * 管理者として認証済みのリクエストを組み立てるテストヘルパー
 *
 * <p>
 * APIキーはテスト側にリテラルを持たず、アプリケーションと同じ設定値（{@code abservice.auth.admin-api-key}）
 * から解決する。
 * </p>
 */
public final class AdminAuth {

    private AdminAuth() {
    }

    /**
     * 管理者APIキーを載せたリクエスト仕様を返します。
     *
     * @return {@code Authorization: Bearer <APIキー>} を付与した RestAssured のリクエスト仕様
     */
    public static RequestSpecification authorized() {
        return given().header("Authorization", "Bearer " + adminApiKey());
    }

    /**
     * @return テスト実行時に有効な管理者APIキー
     */
    public static String adminApiKey() {
        return ConfigProvider.getConfig().getValue("abservice.auth.admin-api-key", String.class);
    }
}
