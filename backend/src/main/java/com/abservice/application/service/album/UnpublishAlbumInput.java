package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム非公開化コマンドの入力DTO
 *
 * @param albumId
 *            非公開化対象のアルバムID
 */
public record UnpublishAlbumInput(@Nullable String albumId) implements CommandService.Input {
}
