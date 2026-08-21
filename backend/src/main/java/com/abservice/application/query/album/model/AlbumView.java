package com.abservice.application.query.album.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * アルバム詳細の Read Model DTO
 *
 * <p>
 * Query 側（CQRS Read）が {@code infrastructure.persistence.datasource}
 * 経由で取得したアルバムを、
 * 読み取り専用の表現として保持します。ドメインオブジェクトではなく、照会結果をそのまま外部（presentation）へ渡すための平坦な DTO です。
 * </p>
 *
 * <p>
 * トラックは本 DTO には含めません（複数の {@code @OneToMany} コレクションを1クエリで JOIN FETCH すると
 * Hibernate の multiple-bag-fetch 制約に抵触するため、集約自身のカラムのみに限定しています。一覧・詳細
 * ユースケースで拡張します）。初出イベント情報（{@code eventName} 以下）は {@code AlbumTableRecord}
 * 自身の直接カラムのため、JOINなしで含められます。
 * </p>
 *
 * @param albumId
 *            アルバムID（ドメインID・UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param releaseDate
 *            リリース日（ISO-8601形式の文字列）
 * @param artistDisplayName
 *            アーティスト表示名
 * @param artistSortKey
 *            アーティストソートキー（nullable）
 * @param catalogNumber
 *            カタログナンバー（nullable）
 * @param isdn
 *            ISDN（nullable）
 * @param eventName
 *            初出イベント名（nullable）
 * @param eventDate
 *            初出イベント開催日（ISO-8601形式の文字列。nullable）
 * @param eventPlace
 *            初出イベント会場（nullable）
 * @param eventSpaceNumber
 *            初出イベントスペース番号（nullable）
 * @param eventNote
 *            初出イベント補足情報（nullable）
 * @param publishedAt
 *            公開日時（nullable。null は下書き。UTC の {@link Instant}）
 * @param coverImageUrl
 *            カバー画像の配信URL（nullable。保管キーと配信設定から組み立てた値で、DBに保存されるのはキーのみ）
 */
public record AlbumView(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn,
        @Nullable String eventName,
        @Nullable String eventDate,
        @Nullable String eventPlace,
        @Nullable String eventSpaceNumber,
        @Nullable String eventNote,
        @Nullable Instant publishedAt,
        @Nullable String coverImageUrl) {
}
