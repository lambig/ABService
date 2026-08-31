package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事のAlbum参照解除コマンドの出力DTO
 *
 * @param articleId
 *            対象の記事ID
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 */
public record RemoveArticleAlbumOutput(
        String articleId,
        String articleType,
        String title) implements CommandService.Output {
}
