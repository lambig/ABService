package com.abservice.application.service.album;

import com.abservice.application.service.CommandService;

/**
 * アルバム削除コマンドの出力DTO
 *
 * <p>
 * べき等のため返す情報を持ちません。
 * </p>
 */
public record DeleteAlbumOutput() implements CommandService.Output {
}
