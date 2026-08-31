package com.abservice.presentation.rest.site.request;

import org.jspecify.annotations.Nullable;

/**
 * サイト文言の登録・更新リクエスト（REST の公開入力契約）
 *
 * <p>
 * 外部からの未検証入力。値検証はアプリケーション層（各値オブジェクトの {@code fromInput}）に委譲する。
 * </p>
 *
 * <p>
 * キーはパスで受け取るため、本文には含めない。
 * </p>
 *
 * @param content
 *            文言の本文
 * @param contentFormat
 *            文言のマークアップ形式（PLAIN_TEXT / MARKDOWN）
 */
public record UpsertSiteContentRequest(
        @Nullable String content,
        @Nullable String contentFormat) {
}
