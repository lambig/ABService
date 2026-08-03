package com.abservice.application.query.albumarticle.model;

import org.jspecify.annotations.Nullable;

/**
 * アルバム記事詳細の Read Model DTO
 *
 * <p>
 * Query 側（CQRS Read）が {@code infrastructure.persistence.datasource}
 * 経由で取得したアルバム記事を、
 * 読み取り専用の表現として保持します。ドメインオブジェクトではなく、照会結果をそのまま外部（presentation）へ渡すための平坦な DTO です。
 * </p>
 *
 * <p>
 * 頒布情報・入手経路は本 DTO には含めません（{@code AlbumArticleTableRecord} 自身のカラムではなく、Album側の
 * 関連エンティティとしてJOINが必要になるため）。一覧・詳細ユースケースで拡張します。
 * </p>
 *
 * @param albumId
 *            アルバム記事ID（ドメインID。対応するAlbum集約のIDと同じ）
 * @param introLong
 *            記事本文としての紹介コメント（nullable）
 * @param introShort
 *            お品書き用のショートコメント（nullable）
 * @param firstEventSpace
 *            初出イベントスペース（nullable）
 * @param labelTag
 *            ラベルタグ（列挙子名。nullable）
 */
public record AlbumArticleView(
        String albumId,
        @Nullable String introLong,
        @Nullable String introShort,
        @Nullable String firstEventSpace,
        @Nullable String labelTag) {
}
