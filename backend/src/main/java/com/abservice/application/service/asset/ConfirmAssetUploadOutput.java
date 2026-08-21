package com.abservice.application.service.asset;

import com.abservice.application.service.CommandService;

/**
 * アップロード確定の出力
 *
 * @param assetKey
 *            確定したアセットキー
 * @param url
 *            公開配信URL（集約に保存する値）
 * @param contentType
 *            実体から判定した Content-Type
 * @param sizeBytes
 *            実体のバイト数
 */
public record ConfirmAssetUploadOutput(
        String assetKey,
        String url,
        String contentType,
        long sizeBytes) implements CommandService.Output {
}
