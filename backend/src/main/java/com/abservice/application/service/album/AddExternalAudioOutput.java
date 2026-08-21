package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * 外部音源追加コマンドの出力DTO
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
public record AddExternalAudioOutput(
        String albumId,
        String externalAudioId,
        int displayOrder,
        String url) implements CommandService.Output {
}
