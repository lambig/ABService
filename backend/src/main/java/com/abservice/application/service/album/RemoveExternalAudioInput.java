package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源削除コマンドの入力DTO
 *
 * @param albumId
 *            削除対象の外部音源が属するアルバムID
 * @param externalAudioId
 *            削除対象の外部音源ID
 */
public record RemoveExternalAudioInput(
        @Nullable String albumId,
        @Nullable String externalAudioId) implements CommandService.Input {
}
