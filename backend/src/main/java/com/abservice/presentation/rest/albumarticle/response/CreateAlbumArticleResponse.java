package com.abservice.presentation.rest.albumarticle.response;

import org.jspecify.annotations.Nullable;

/**
 * アルバム記事作成レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            対応するAlbum集約のID（本集約のID）
 * @param introShort
 *            お品書き用のショートコメント（nullable）
 * @param labelTag
 *            ラベルタグ（列挙子名。nullable）
 */
public record CreateAlbumArticleResponse(
        String albumId,
        @Nullable String introShort,
        @Nullable String labelTag) {
}
