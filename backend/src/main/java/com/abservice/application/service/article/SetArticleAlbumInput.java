package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 記事へのAlbum参照設定コマンドの入力DTO
 *
 * @param articleId
 *            対象の記事ID
 * @param albumId
 *            紐付けるアルバムID
 */
public record SetArticleAlbumInput(@Nullable String articleId, @Nullable String albumId)
        implements
            CommandService.Input {
}
