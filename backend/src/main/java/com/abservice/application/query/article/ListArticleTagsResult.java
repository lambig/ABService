package com.abservice.application.query.article;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.article.model.ArticleTagView;
import java.util.List;

/**
 * 記事タグ一覧照会の結果
 *
 * @param items
 *            タグの Read Model のリスト（名前の昇順）
 */
public record ListArticleTagsResult(List<ArticleTagView> items) implements QueryService.Result {
}
