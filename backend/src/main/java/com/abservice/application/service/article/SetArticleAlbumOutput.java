package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事へのAlbum参照設定コマンドの出力DTO
 *
 * @param articleId
 *            対象の記事ID
 * @param revision
 *            紐付け後の記事の世代。次の更新の{@code expectedRevision}に使う
 * @param articleType
 *            記事種別（列挙子名）
 * @param albumId
 *            紐付けられたアルバムID
 * @param title
 *            記事タイトル
 */
public record SetArticleAlbumOutput(
        String articleId,
        int revision,
        String articleType,
        String albumId,
        String title) implements CommandService.Output {
}
