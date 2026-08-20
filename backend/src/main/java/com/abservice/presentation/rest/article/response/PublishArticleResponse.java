package com.abservice.presentation.rest.article.response;

/**
 * 記事公開レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            公開された記事のID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publicFlag
 *            公開フラグ（公開成功時は常にtrue）
 */
public record PublishArticleResponse(
        String articleId,
        String articleType,
        String title,
        boolean publicFlag) {
}
