package com.abservice.presentation.rest.exception;

import org.jspecify.annotations.Nullable;

/**
 * フィールド単位の検証エラー（RFC 9457 の {@code errors} 拡張メンバの要素）
 *
 * @param field
 *            エラーが発生したフィールド名
 * @param message
 *            人間可読なエラーメッセージ
 * @param code
 *            機械可読なエラーコード（nullable）
 */
public record FieldError(String field, String message, @Nullable String code) {
}
