package com.abservice.application.query.article;

import com.abservice.application.query.QueryService;

/**
 * 記事タグ一覧照会のクエリ
 *
 * <p>
 * 条件を持たない。タグは管理画面が選択肢として引くための語彙であり、件数が増えてもページングを要する規模に
 * ならない（記事1本あたり数個、語彙全体で数十件）。
 * </p>
 */
public record ListArticleTagsQuery() implements QueryService.Query {
}
