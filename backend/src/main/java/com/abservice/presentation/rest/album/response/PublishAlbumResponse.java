package com.abservice.presentation.rest.album.response;

/**
 * アルバム公開レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            公開されたアルバムのID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param published
 *            公開状態（公開成功時は常にtrue）
 */
public record PublishAlbumResponse(
        String albumId,
        String title,
        boolean published) {
}
