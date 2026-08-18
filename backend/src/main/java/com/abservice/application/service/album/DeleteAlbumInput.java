package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム削除コマンドの入力DTO
 *
 * @param albumId
 *            削除対象のアルバムID
 */
public record DeleteAlbumInput(@Nullable String albumId) implements CommandService.Input {
}
