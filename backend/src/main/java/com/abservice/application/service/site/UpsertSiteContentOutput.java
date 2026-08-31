package com.abservice.application.service.site;

import com.abservice.application.service.CommandService;

/**
 * サイト文言の登録・更新コマンドの出力DTO
 *
 * @param key
 *            どの文言かを指すキー
 * @param content
 *            文言の本文
 * @param contentFormat
 *            文言のマークアップ形式（列挙子名）
 */
public record UpsertSiteContentOutput(
        String key,
        String content,
        String contentFormat) implements CommandService.Output {
}
