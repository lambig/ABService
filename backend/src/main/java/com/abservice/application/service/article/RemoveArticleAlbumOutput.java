package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事のAlbum参照解除コマンドの出力DTO
 *
 * @param articleId
 *            対象の記事ID
 * @param revision
 *            解除後の記事の世代。次の更新の{@code expectedRevision}に使う
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 */
public record RemoveArticleAlbumOutput(
        String articleId,
        int revision,
        String articleType,
        String title) implements CommandService.Output {
}
