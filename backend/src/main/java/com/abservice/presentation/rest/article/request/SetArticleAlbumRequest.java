package com.abservice.presentation.rest.article.request;

import org.jspecify.annotations.Nullable;

/**
 * 記事へのAlbum参照設定リクエスト（REST の公開入力契約）
 *
 * @param albumId
 *            紐付けるアルバムID
 */
public record SetArticleAlbumRequest(@Nullable String albumId) {
}
