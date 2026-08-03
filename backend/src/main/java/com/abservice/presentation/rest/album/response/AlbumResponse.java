package com.abservice.presentation.rest.album.response;

import org.jspecify.annotations.Nullable;

/**
 * アルバム詳細レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            アルバムID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param catalogNumber
 *            カタログナンバー（nullable）
 * @param isdn
 *            ISDN（nullable）
 */
public record AlbumResponse(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn) {
}
