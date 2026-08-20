package com.abservice.presentation.rest.article.response;

/**
 * 記事へのAlbum参照設定レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            対象の記事ID（UUIDv7形式の文字列）
 * @param articleType
 *            記事種別（列挙子名）
 * @param albumId
 *            紐付けられたアルバムID
 * @param title
 *            記事タイトル
 */
public record SetArticleAlbumResponse(
        String articleId,
        String articleType,
        String albumId,
        String title) {
}
