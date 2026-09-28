package com.abservice.application.port;

/**
 * 配信対象として確定した実体の実測値。保管先が確定時に計算した値で、バックエンドは実体を読まない。
 *
 * @param byteLength
 *            実体のバイト数
 * @param sha256
 *            実体全体のSHA-256（小文字hex）
 */
public record StoredAssetDigest(long byteLength, String sha256) {
}
