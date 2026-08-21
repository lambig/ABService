package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 外部音源追加コマンドの入力DTO
 *
 * @param albumId
 *            追加先のアルバムID
 * @param url
 *            外部音源の埋め込み元URL
 */
public record AddExternalAudioInput(
        @Nullable String albumId,
        @Nullable String url) implements CommandService.Input {
}
