package com.abservice.lib;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * エラー結果を表します
 *
 * @param field
 *            エラーが発生したフィールド名
 * @param message
 *            エラーメッセージ
 * @param code
 *            エラーコード（オプション）
 */
public record ErrorResult(String field, String message, @Nullable String code) {

    public ErrorResult {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * コードなしでエラー結果を生成します
     *
     * @param field
     *            エラーが発生したフィールド名
     * @param message
     *            エラーメッセージ
     */
    public ErrorResult(String field, String message) {
        this(field, message, null);
    }

    @Override
    public String toString() {
        return code != null ? field + ": " + message + " (code: " + code + ")" : field + ": " + message;
    }
}
