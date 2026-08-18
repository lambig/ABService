package com.abservice.application.service.albumarticle;

import com.abservice.application.service.CommandService;

/**
 * アルバム記事削除コマンドの出力DTO
 *
 * <p>
 * べき等のため返す情報を持ちません。
 * </p>
 */
public record DeleteAlbumArticleOutput() implements CommandService.Output {
}
