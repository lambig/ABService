package com.abservice.application.query.article;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.article.model.ArticleView;
import java.util.List;

/**
 * 記事一覧照会の結果
 *
 * <p>
 * 一覧は空リストも正常系のため、{@link GetArticleResult} のような Found/NotFound の分岐は不要で 単一の
 * record として表現する。
 * </p>
 *
 * @param items
 *            記事の Read Model のリスト（このページ分）
 * @param page
 *            ページ番号（0始まり、クランプ後の値）
 * @param size
 *            1ページの件数（クランプ後の値）
 * @param totalElements
 *            全件数
 * @param totalPages
 *            総ページ数
 */
public record ListArticlesResult(
        List<ArticleView> items,
        int page,
        int size,
        long totalElements,
        int totalPages) implements QueryService.Result {
}
