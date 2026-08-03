package com.abservice.presentation.rest.album.response;

/**
 * アルバム作成レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            生成されたアルバムのID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 */
public record CreateAlbumResponse(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName) {
}
