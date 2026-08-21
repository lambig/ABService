package com.abservice.application.query.article;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;

/**
 * 記事詳細照会クエリ
 *
 * @param articleId
 *            照会する記事のドメインID（UUIDv7形式の文字列）
 * @param audience
 *            要求元（公開向けは公開中のみ、管理向けは下書きも対象）
 */
public record GetArticleQuery(String articleId, Audience audience) implements QueryService.Query {
}
