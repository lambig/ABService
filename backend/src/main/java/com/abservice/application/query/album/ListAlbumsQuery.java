package com.abservice.application.query.album;

import com.abservice.application.query.QueryService;

/**
 * アルバム一覧照会のクエリ（ページネーション付き）
 *
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 */
public record ListAlbumsQuery(int page, int size) implements QueryService.Query {
}
