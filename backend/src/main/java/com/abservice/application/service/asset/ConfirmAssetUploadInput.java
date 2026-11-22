package com.abservice.application.service.asset;

import com.abservice.application.service.CommandService;
import com.abservice.domain.model.policy.Policy;

/**
 * アップロード確定の入力
 *
 * @param assetKey
 *            アップロードURL発行時に払い出されたアセットキー
 */
public record ConfirmAssetUploadInput(
        String assetKey) implements CommandService.Input<ConfirmAssetUploadInput> {
    /** キーの受入判定は保管先の境界が持ち、入力では別の形式制限を追加しない。 */
    @Override
    public Policy<ConfirmAssetUploadInput> policy() {
        return Policy.all();
    }
}
