package com.abservice.application.query.tune;

import com.abservice.application.query.QueryService;

/**
 * チューン一覧照会のクエリ（ページネーション付き）
 *
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 */
public record ListTunesQuery(int page, int size) implements QueryService.Query {
}
