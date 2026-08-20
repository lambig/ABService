package com.abservice.presentation.rest.article.response;

/**
 * 記事非公開化レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            非公開化された記事のID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publicFlag
 *            公開フラグ（非公開化成功時は常にfalse）
 */
public record UnpublishArticleResponse(
        String articleId,
        String articleType,
        String title,
        boolean publicFlag) {
}
