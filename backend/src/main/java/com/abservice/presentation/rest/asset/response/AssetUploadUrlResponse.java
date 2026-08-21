package com.abservice.presentation.rest.asset.response;

import java.time.Instant;

/**
 * アップロードURL発行レスポンス
 *
 * @param assetKey
 *            確定時に指定するアセットキー
 * @param uploadUrl
 *            この宛先へ Content-Type を付けて PUT する
 * @param expiresAt
 *            署名付きURLの有効期限（UTC）
 * @param maxBytes
 *            許容される最大バイト数（超過分は確定時に拒否される）
 */
public record AssetUploadUrlResponse(
        String assetKey,
        String uploadUrl,
        Instant expiresAt,
        long maxBytes) {
}
