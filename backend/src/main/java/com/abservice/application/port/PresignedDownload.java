package com.abservice.application.port;

import java.time.Instant;

/**
 * 取得用の署名付きURL
 *
 * @param url
 *            クライアントが GET する先のURL（署名済み）
 * @param expiresAt
 *            URLの有効期限（UTC）
 */
public record PresignedDownload(String url, Instant expiresAt) {
}
