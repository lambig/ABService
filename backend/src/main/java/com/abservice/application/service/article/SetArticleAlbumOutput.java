package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事へのAlbum参照設定コマンドの出力DTO
 *
 * @param articleId
 *            対象の記事ID
 * @param articleType
 *            記事種別（列挙子名）
 * @param albumId
 *            紐付けられたアルバムID
 * @param title
 *            記事タイトル
 */
public record SetArticleAlbumOutput(
        String articleId,
        String articleType,
        String albumId,
        String title) implements CommandService.Output {
}
