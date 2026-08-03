package com.abservice.presentation.rest.albumarticle.response;

import org.jspecify.annotations.Nullable;

/**
 * アルバム記事詳細レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            アルバム記事ID（対応するAlbum集約のIDと同じ）
 * @param introLong
 *            記事本文としての紹介コメント（nullable）
 * @param introShort
 *            お品書き用のショートコメント（nullable）
 * @param firstEventSpace
 *            初出イベントスペース（nullable）
 * @param labelTag
 *            ラベルタグ（列挙子名。nullable）
 */
public record AlbumArticleResponse(
        String albumId,
        @Nullable String introLong,
        @Nullable String introShort,
        @Nullable String firstEventSpace,
        @Nullable String labelTag) {
}
