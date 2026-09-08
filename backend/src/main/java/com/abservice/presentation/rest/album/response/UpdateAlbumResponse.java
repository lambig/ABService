package com.abservice.presentation.rest.album.response;

/**
 * アルバム更新レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            更新されたアルバムのID（UUIDv7形式の文字列）
 * @param revision
 *            保存後の世代。同じ画面で続けて編集する場合、次の更新はこれを {@code expectedRevision}
 *            として送る（#287）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 */
public record UpdateAlbumResponse(
        String albumId,
        int revision,
        String title,
        String releaseDate,
        String artistDisplayName) {
}
