package com.abservice.presentation.rest.article.response;

/**
 * 記事タグ追加レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            記事ID（UUIDv7形式の文字列）
 * @param tagId
 *            付与したタグのID（UUIDv7形式の文字列）
 * @param name
 *            タグ名
 */
public record AddArticleTagResponse(String articleId, String tagId, String name) {
}
