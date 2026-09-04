package com.abservice.presentation.rest.album.response;

import java.util.List;

/**
 * アルバム非公開化の前提の応答
 *
 * @param articlesBecomingUnpublished
 *            連動して非公開へ戻る記事（該当なしの場合は空）
 */
public record AlbumUnpublicationPreconditionsResponse(List<CascadeUnpublishedArticle> articlesBecomingUnpublished) {

    /**
     * 連動して非公開へ戻る記事
     *
     * @param articleId
     *            記事のドメインID
     * @param title
     *            記事のタイトル
     */
    public record CascadeUnpublishedArticle(String articleId, String title) {
    }
}
