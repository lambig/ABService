package com.abservice.presentation.rest.album.response;

/**
 * トラック追加レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            追加先のアルバムID
 * @param trackId
 *            追加されたトラックのID
 * @param trackNo
 *            トラック番号
 * @param title
 *            トラックタイトル
 */
public record AddTrackResponse(
        String albumId,
        String trackId,
        int trackNo,
        String title) {
}
