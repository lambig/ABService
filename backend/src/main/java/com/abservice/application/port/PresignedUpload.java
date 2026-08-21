package com.abservice.application.port;

import java.time.Instant;

/**
 * アップロード用の署名付きURL
 *
 * @param url
 *            クライアントが PUT する先のURL（署名済み）
 * @param expiresAt
 *            URLの有効期限（UTC）
 */
public record PresignedUpload(String url, Instant expiresAt) {
}
