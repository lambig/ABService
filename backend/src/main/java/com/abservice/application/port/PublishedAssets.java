package com.abservice.application.port;

import io.smallrye.mutiny.Uni;

/**
 * 確定した画像アセットの実測値を永続化する境界。配信・公開状態には関与しない。
 *
 * <p>
 * 記録は独立したトランザクションで完了してから応答する。同じキーへの再記録は置き換える（確定は一度きりなので、置き換えが
 * 起きるのは記録を補う再確定が重なった場合に限り、値は保管先の同じ実体から読むため変わらない）。
 * </p>
 */
public interface PublishedAssets {
    Uni<Void> record(PublishedAsset asset);

    /** 実測値が記録済みか。確定済みで記録の無いキーを、同じキーの再確定で補うために使う。 */
    Uni<Boolean> isRecorded(String assetKey);
}
