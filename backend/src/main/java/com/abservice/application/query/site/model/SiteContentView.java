package com.abservice.application.query.site.model;

/**
 * サイト文言の Read Model
 *
 * <p>
 * ドメインIDは持ちません。文言を引くのはキーであり、IDを外部へ出す経路を持たないためです（オブジェクト レジストリ #174
 * が必要になった時点で判断します）。
 * </p>
 *
 * @param key
 *            どの文言かを指すキー
 * @param content
 *            文言の本文
 * @param contentFormat
 *            文言のマークアップ形式（列挙子名）
 */
public record SiteContentView(
        String key,
        String content,
        String contentFormat) {
}
