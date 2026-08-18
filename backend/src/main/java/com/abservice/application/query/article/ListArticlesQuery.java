package com.abservice.application.query.article;

import com.abservice.application.query.QueryService;

/**
 * 記事一覧照会のクエリ（ページネーション付き）
 *
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 */
public record ListArticlesQuery(int page, int size) implements QueryService.Query {
}
