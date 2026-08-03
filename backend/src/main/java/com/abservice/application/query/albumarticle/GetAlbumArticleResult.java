package com.abservice.application.query.albumarticle;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.albumarticle.model.AlbumArticleView;

/**
 * アルバム記事詳細照会の結果
 *
 * <p>
 * 「未存在」を例外ではなく正常な結果の一種として型で表現します（sealed）。presentation 層は各バリアントで switch
 * し、{@link Found} を 200、{@link NotFound} を 404 に対応づけます。
 * </p>
 */
public sealed interface GetAlbumArticleResult extends QueryService.Result
        permits GetAlbumArticleResult.Found, GetAlbumArticleResult.NotFound {

    /**
     * アルバム記事が見つかった結果（→ 200）
     *
     * @param article
     *            アルバム記事の Read Model
     */
    record Found(AlbumArticleView article) implements GetAlbumArticleResult {
    }

    /**
     * アルバム記事が見つからなかった結果（→ 404）
     */
    record NotFound() implements GetAlbumArticleResult {
    }
}
