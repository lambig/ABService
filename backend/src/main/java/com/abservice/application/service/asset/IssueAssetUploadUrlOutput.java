package com.abservice.application.service.asset;

import com.abservice.application.service.CommandService;
import java.time.Instant;

/**
 * アップロードURL発行の出力
 *
 * @param assetKey
 *            発行したアセットキー（確定時に指定する）
 * @param uploadUrl
 *            クライアントが PUT する先の署名付きURL
 * @param expiresAt
 *            署名付きURLの有効期限（UTC）
 * @param maxBytes
 *            アップロードを許容する最大バイト数（超過分は確定時に拒否される）
 */
public record IssueAssetUploadUrlOutput(
        String assetKey,
        String uploadUrl,
        Instant expiresAt,
        long maxBytes) implements CommandService.Output {
}
