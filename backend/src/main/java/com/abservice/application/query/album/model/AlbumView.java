package com.abservice.application.query.album.model;

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
 * トラック・イベント頒布情報は本 DTO には含めません（複数の {@code @OneToMany} コレクションを1クエリで JOIN FETCH
 * すると Hibernate の multiple-bag-fetch 制約に抵触するため、集約自身のカラムのみに限定しています。
 * 一覧・詳細ユースケースで拡張します）。
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
 */
public record AlbumView(
        String albumId,
        String title,
        String releaseDate,
        String artistDisplayName,
        @Nullable String artistSortKey,
        @Nullable String catalogNumber,
        @Nullable String isdn) {
}
