package com.abservice.presentation.rest.asset.response;

/**
 * アップロード確定レスポンス
 *
 * @param assetKey
 *            確定したアセットキー
 * @param url
 *            公開配信URL（集約のフィールドに保存する値）
 * @param contentType
 *            実体から判定した Content-Type
 * @param sizeBytes
 *            実体のバイト数
 */
public record ConfirmAssetUploadResponse(
        String assetKey,
        String url,
        String contentType,
        long sizeBytes) {
}
