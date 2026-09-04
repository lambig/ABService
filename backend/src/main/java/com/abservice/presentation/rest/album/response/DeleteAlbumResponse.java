package com.abservice.presentation.rest.album.response;

import java.util.List;

/**
 * アルバム削除のレスポンス（REST の公開出力契約）
 *
 * <p>
 * 削除に伴い、当該アルバムを参照していた記事は参照を失効し、公開中だったものは非公開へ戻ります。フロントで通知するため、 影響を受けた記事を返します。
 * </p>
 *
 * @param affectedArticles
 *            影響を受けた記事の一覧（該当なしの場合は空）
 */
public record DeleteAlbumResponse(List<DeletionAffectedArticle> affectedArticles) {

    /**
     * 影響を受けた記事
     *
     * @param articleId
     *            記事ID
     * @param title
     *            記事タイトル
     * @param unpublished
     *            公開中だったため非公開へ戻したか
     */
    public record DeletionAffectedArticle(String articleId, String title, boolean unpublished) {
    }
}
