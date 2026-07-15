package com.abservice.application.query.article;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.article.model.ArticleView;

/**
 * 記事詳細照会の結果
 *
 * <p>
 * 「未存在」を例外ではなく正常な結果の一種として型で表現します（sealed）。presentation 層は各バリアントで switch
 * し、{@link Found} を 200、{@link NotFound} を 404 に対応づけます。将来
 * INSUFFICIENT_DATA（422） などのバリアントを追加できます。
 * </p>
 */
public sealed interface GetArticleResult extends QueryService.Result
        permits GetArticleResult.Found, GetArticleResult.NotFound {

    /**
     * 記事が見つかった結果（→ 200）
     *
     * @param article
     *            記事の Read Model
     */
    record Found(ArticleView article) implements GetArticleResult {
    }

    /**
     * 記事が見つからなかった結果（→ 404）
     */
    record NotFound() implements GetArticleResult {
    }
}
