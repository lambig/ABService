package com.abservice.presentation.rest.security.response;

import java.time.Instant;

/**
 * キー交換の応答。トークンはログへ出さず、この応答だけでクライアントへ渡す。
 *
 * @param token
 *            期限付きの不透明Bearerトークン
 * @param expiresAt
 *            有効期限（この時刻以降は無効。利用による延長はない）
 */
public record AdminSessionResponse(String token, Instant expiresAt) {

    @Override
    public String toString() {
        return "AdminSessionResponse[token=<redacted>, expiresAt=%s]".formatted(expiresAt);
    }
}
