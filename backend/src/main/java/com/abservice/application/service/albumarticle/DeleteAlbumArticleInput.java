package com.abservice.application.service.albumarticle;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム記事削除コマンドの入力DTO
 *
 * @param albumId
 *            削除対象のアルバム記事ID（対応するAlbum集約のID）
 */
public record DeleteAlbumArticleInput(@Nullable String albumId) implements CommandService.Input {
}
