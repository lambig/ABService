package com.abservice.application.service.site;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * サイト文言の登録・更新コマンドの入力DTO
 *
 * @param key
 *            どの文言かを指すキー
 * @param content
 *            文言の本文
 * @param contentFormat
 *            文言のマークアップ形式（列挙子名）
 */
public record UpsertSiteContentInput(
        @Nullable String key,
        @Nullable String content,
        @Nullable String contentFormat) implements CommandService.Input {
}
