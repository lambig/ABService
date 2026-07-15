package com.abservice.application.query.article;

import com.abservice.application.query.QueryService;

/**
 * 記事詳細照会クエリ
 *
 * @param articleId
 *            照会する記事のドメインID（UUIDv7形式の文字列）
 */
public record GetArticleQuery(String articleId) implements QueryService.Query {
}
