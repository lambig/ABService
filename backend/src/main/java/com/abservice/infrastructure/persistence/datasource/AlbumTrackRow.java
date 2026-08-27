package com.abservice.infrastructure.persistence.datasource;

import org.jspecify.annotations.Nullable;

/**
 * アルバムのトラックの照会結果1行
 *
 * <p>
 * Query側（CQRS Read）がアルバム本体とは別クエリでトラックを読むための平坦な投影です。エンティティを返すと
 * 親アルバムが遅延プロキシになり、チューン構成の初期化も必要になる（Reactiveではセッション内でも同期初期化
 * できない）ため、必要な列だけを直接受け取ります。
 * </p>
 *
 * @param trackId
 *            トラックのドメインID（UUIDv7形式の文字列）
 * @param trackNo
 *            アルバム内のトラック番号
 * @param title
 *            トラックタイトル
 * @param artistDisplayName
 *            トラック個別のアーティスト表示名（nullable。null はアルバムの名義を継承）
 * @param artistSortKey
 *            トラック個別のアーティストソートキー（nullable）
 */
public record AlbumTrackRow(
        String trackId,
        Integer trackNo,
        String title,
        @Nullable String artistDisplayName,
        @Nullable String artistSortKey) {
}
