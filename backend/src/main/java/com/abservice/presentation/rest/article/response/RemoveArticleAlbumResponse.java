package com.abservice.presentation.rest.article.response;

/**
 * 記事のAlbum参照解除レスポンス（REST の公開出力契約）
 *
 * @param articleId
 *            対象の記事ID（UUIDv7形式の文字列）
 * @param revision
 *            解除後の記事の世代。次の更新の{@code expectedRevision}に使う
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 */
public record RemoveArticleAlbumResponse(
        String articleId,
        int revision,
        String articleType,
        String title) {
}
