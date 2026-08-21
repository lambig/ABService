package com.abservice.application.service.asset;

import com.abservice.application.service.CommandService;

/**
 * アップロード確定の入力
 *
 * @param assetKey
 *            アップロードURL発行時に払い出されたアセットキー
 */
public record ConfirmAssetUploadInput(String assetKey) implements CommandService.Input {
}
