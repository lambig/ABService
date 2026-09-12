package com.abservice.application.port;

import org.jspecify.annotations.Nullable;

/**
 * 保管済みアセットの先頭バイト列とメタ情報
 *
 * <p>
 * 実体の検査に必要な情報を1回の範囲取得（Range GET）でまとめて得るための表現。{@code prefix} は要求した長さより
 * 短くなり得る（実体がそれより小さい場合）。
 * </p>
 *
 * <p>
 * {@code entityTag} は「いま検査したこの実体」を指す。検査と確定の間に同じキーへ別の実体が置かれ得るため、確定は
 * これを条件にして、変わっていれば失敗させる（#285）。
 * </p>
 *
 * @param prefix
 *            先頭バイト列
 * @param totalBytes
 *            実体全体のバイト数
 * @param contentType
 *            保管先が保持している Content-Type（アップロード時の申告値。nullable）
 * @param entityTag
 *            保管先が付けた実体の識別子（ETag）
 */
public record StoredAssetHead(
        byte[] prefix,
        long totalBytes,
        @Nullable String contentType,
        String entityTag) {
}
