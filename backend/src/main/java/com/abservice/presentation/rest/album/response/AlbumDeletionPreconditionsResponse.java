package com.abservice.presentation.rest.album.response;

import java.util.List;

/**
 * アルバム削除の前提の応答
 *
 * @param affectedArticles
 *            削除によって影響を受ける記事（該当なしの場合は空）
 */
public record AlbumDeletionPreconditionsResponse(List<PreconditionAffectedArticle> affectedArticles) {

    /**
     * 削除が参照元の記事1件に及ぼす影響
     *
     * @param articleId
     *            記事のドメインID
     * @param title
     *            記事のタイトル
     * @param losesAlbumReference
     *            アルバム参照が失効するか
     * @param becomesUnpublished
     *            公開中だったために非公開へ戻るか
     */
    public record PreconditionAffectedArticle(
            String articleId,
            String title,
            boolean losesAlbumReference,
            boolean becomesUnpublished) {
    }
}
