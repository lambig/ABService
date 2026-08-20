package com.abservice.presentation.rest.album.response;

/**
 * トラック更新レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            更新対象トラックが属するアルバムID
 * @param trackId
 *            更新されたトラックのID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 */
public record UpdateTrackResponse(
        String albumId,
        String trackId,
        int trackNo,
        String title) {
}
