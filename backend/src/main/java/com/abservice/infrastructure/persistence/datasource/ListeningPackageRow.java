package com.abservice.infrastructure.persistence.datasource;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 配布パッケージの snapshot の1行
 *
 * <p>
 * 公開済みでクロスフェードが確定している作品を、収録曲とチューン構成まで1つの問い合わせで平坦に読む。行の並びは
 * 作品（公開向け一覧の既定順）・曲順・登場順で、同じ作品・曲は複数行にわたる。作品に曲が無ければ曲の列は null、
 * 曲にチューン構成が無ければチューンの列は null になる。artwork は作品のカバー画像が確定済みで実測値を持つときだけ 値を持ち、無ければ
 * artwork の列はすべて null になる。
 * </p>
 *
 * @param albumId
 *            作品のドメインID
 * @param albumTitle
 *            作品名
 * @param audioId
 *            確定済みクロスフェード音源のID
 * @param byteLength
 *            音源の実測バイト数
 * @param sha256
 *            音源のSHA-256（小文字hex）
 * @param sampleRate
 *            音源のサンプルレート
 * @param totalSamples
 *            音源のチャンネル当たりサンプル数
 * @param artworkKey
 *            カバー画像のアセットキー（実測値を持つ確定画像が無ければ null）
 * @param artworkContentType
 *            カバー画像の Content-Type（同上）
 * @param artworkByteLength
 *            カバー画像の実測バイト数（同上）
 * @param artworkSha256
 *            カバー画像のSHA-256（同上）
 * @param trackId
 *            収録曲のドメインID（曲が無ければ null）
 * @param trackNo
 *            収録曲の番号（曲が無ければ null）
 * @param trackTitle
 *            入力された曲名（無ければ null。名はチューン名から組む）
 * @param tuneSeq
 *            チューン構成の登場順（構成が無ければ null）
 * @param tuneTitle
 *            チューン名（無ければ null）
 */
public record ListeningPackageRow(
        String albumId,
        String albumTitle,
        UUID audioId,
        long byteLength,
        String sha256,
        int sampleRate,
        long totalSamples,
        @Nullable String artworkKey,
        @Nullable String artworkContentType,
        @Nullable Long artworkByteLength,
        @Nullable String artworkSha256,
        @Nullable String trackId,
        @Nullable Integer trackNo,
        @Nullable String trackTitle,
        @Nullable Integer tuneSeq,
        @Nullable String tuneTitle) {
}
