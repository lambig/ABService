package com.abservice.application.port;

import io.smallrye.mutiny.Uni;

/**
 * 確定した画像アセットの実測値を永続化する境界。配信・公開状態には関与しない。
 *
 * <p>
 * 記録は独立したトランザクションで完了してから応答する。同じキーへの再記録は置き換える（確定は一度きりなので、置き換えが
 * 起きるのは記録だけが失敗した確定のやり直しに限る）。
 * </p>
 */
public interface PublishedAssets {
    Uni<Void> record(PublishedAsset asset);
}
