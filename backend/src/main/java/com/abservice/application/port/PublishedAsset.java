package com.abservice.application.port;

import java.util.Objects;

/**
 * 確定した画像アセットの実測値。試聴端末へ渡す表示素材の識別に使う。
 *
 * @param assetKey
 *            アセットキー（配信キー）
 * @param contentType
 *            実体から判定した Content-Type
 * @param byteLength
 *            確定した実体のバイト数
 * @param sha256
 *            確定した実体のSHA-256（小文字hex）
 */
public record PublishedAsset(
        String assetKey,
        String contentType,
        long byteLength,
        String sha256) {
    public PublishedAsset {
        Objects.requireNonNull(assetKey);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(sha256);
    }
}
