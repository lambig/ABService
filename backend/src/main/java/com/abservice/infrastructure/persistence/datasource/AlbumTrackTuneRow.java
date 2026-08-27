package com.abservice.infrastructure.persistence.datasource;

import org.jspecify.annotations.Nullable;

/**
 * トラック内のチューン構成の照会結果1行
 *
 * <p>
 * Query側（CQRS Read）がトラックとは別クエリでチューン構成を読むための平坦な投影です。所属トラックは
 * 内部IDではなくドメインIDで受け取り、トラックの投影（{@link AlbumTrackRow}）と同じ識別子で突き合わせます。
 * </p>
 *
 * @param trackId
 *            所属トラックのドメインID（UUIDv7形式の文字列）
 * @param seq
 *            トラック内での登場順（1, 2, 3, ...）
 * @param tuneTitle
 *            チューン名（nullable）
 * @param composerCreditOverride
 *            作曲者クレジット（nullable）
 * @param arrangerCreditOverride
 *            アレンジャークレジット（nullable）
 * @param linkUrl
 *            リンクURL（nullable）
 */
public record AlbumTrackTuneRow(
        String trackId,
        Integer seq,
        @Nullable String tuneTitle,
        @Nullable String composerCreditOverride,
        @Nullable String arrangerCreditOverride,
        @Nullable String linkUrl) {
}
