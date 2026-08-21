package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * 外部音源削除コマンドの出力DTO
 *
 * @param albumId
 *            削除対象の外部音源が属していたアルバムID
 * @param externalAudioId
 *            削除された外部音源のID
 */
public record RemoveExternalAudioOutput(
        String albumId,
        String externalAudioId) implements CommandService.Output {
}
