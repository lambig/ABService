package com.abservice.presentation.rest.article.response;

/**
 * 管理向けタグ1件のレスポンス（REST の公開出力契約）
 *
 * <p>
 * 管理画面はタグを外す対象を同定するためタグIDを使う。公開サイトはタグ名を並べるだけのため、公開向けの応答は 名前の配列を返しIDを持たない。
 * </p>
 *
 * @param tagId
 *            タグID（UUIDv7形式の文字列）
 * @param name
 *            タグ名
 */
public record AdminArticleTagResponse(String tagId, String name) {
}
