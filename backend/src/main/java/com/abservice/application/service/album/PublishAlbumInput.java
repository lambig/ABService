package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム公開コマンドの入力DTO
 *
 * @param albumId
 *            公開対象のアルバムID
 */
public record PublishAlbumInput(@Nullable String albumId) implements CommandService.Input {
}
