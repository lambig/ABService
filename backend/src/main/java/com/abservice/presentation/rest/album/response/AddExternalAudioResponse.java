package com.abservice.presentation.rest.album.response;

/**
 * 外部音源追加レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            追加先のアルバムID
 * @param externalAudioId
 *            追加された外部音源のID
 * @param displayOrder
 *            アルバム内での表示順
 * @param url
 *            外部音源の埋め込み元URL
 */
public record AddExternalAudioResponse(
        String albumId,
        String externalAudioId,
        int displayOrder,
        String url) {
}
