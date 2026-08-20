package com.abservice.presentation.rest.album.response;

import java.util.List;

/**
 * アルバム非公開化レスポンス（REST の公開出力契約）
 *
 * @param albumId
 *            非公開化されたアルバムのID（UUIDv7形式の文字列）
 * @param title
 *            アルバムタイトル
 * @param published
 *            公開状態（非公開化成功時は常にfalse）
 * @param cascadeUnpublishedArticles
 *            当該アルバムを参照していたために連動して非公開化された記事の一覧（該当なしの場合は空）
 */
public record UnpublishAlbumResponse(
        String albumId,
        String title,
        boolean published,
        List<CascadeUnpublishedArticleResponse> cascadeUnpublishedArticles) {

    /**
     * カスケード非公開化された記事の要約情報
     *
     * @param articleId
     *            記事ID
     * @param title
     *            記事タイトル
     */
    public record CascadeUnpublishedArticleResponse(String articleId, String title) {
    }
}
