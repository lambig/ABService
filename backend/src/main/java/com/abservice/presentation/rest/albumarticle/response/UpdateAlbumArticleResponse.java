package com.abservice.presentation.rest.albumarticle.response;

import org.jspecify.annotations.Nullable;

/**
 * アルバム記事更新レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            更新されたアルバム記事のID（対応するAlbum集約のID）
 * @param introShort
 *            お品書き用のショートコメント（nullable）
 * @param labelTag
 *            ラベルタグ（列挙子名。nullable）
 */
public record UpdateAlbumArticleResponse(
        String albumId,
        @Nullable String introShort,
        @Nullable String labelTag) {
}
