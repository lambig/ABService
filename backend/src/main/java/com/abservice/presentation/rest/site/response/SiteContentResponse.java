package com.abservice.presentation.rest.site.response;

/**
 * サイト文言のレスポンス（REST の公開出力契約）
 *
 * <p>
 * 公開向けと管理向けで同じ表現を返す。文言は「どちらから見ても同じもの」であり、要求元で項目が変わらないため （`docs/DECISIONS.md` 20
 * の考え方に照らして、分ける理由がない）。
 * </p>
 *
 * <p>
 * ドメインIDは含まない。文言を引くのはキーであり、IDを外部へ出す経路を持たない。
 * </p>
 *
 * @param key
 *            どの文言かを指すキー
 * @param content
 *            文言の本文
 * @param contentFormat
 *            文言のマークアップ形式（列挙子名）
 */
public record SiteContentResponse(
        String key,
        String content,
        String contentFormat) {
}
