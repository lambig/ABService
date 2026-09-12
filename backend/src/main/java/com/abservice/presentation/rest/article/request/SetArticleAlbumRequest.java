package com.abservice.presentation.rest.article.request;

import org.jspecify.annotations.Nullable;

/**
 * 記事へのAlbum参照設定リクエスト（REST の公開入力契約）
 *
 * @param albumId
 *            紐付けるアルバムID
 * @param expectedRevision
 *            編集を始めた時点の記事の世代（必須）。紐付けは記事の世代を進めるため、更新（PUT
 *            /articles/{id}）と同じ楽観ロック契約を適用する
 */
public record SetArticleAlbumRequest(@Nullable String albumId, @Nullable Integer expectedRevision) {
}
