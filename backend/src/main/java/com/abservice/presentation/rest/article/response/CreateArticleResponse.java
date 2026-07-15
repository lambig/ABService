package com.abservice.presentation.rest.article.response;

/**
 * 記事作成レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            生成された記事のID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publicFlag
 *            公開フラグ
 */
public record CreateArticleResponse(
        String articleId,
        String articleType,
        String title,
        boolean publicFlag) {
}
