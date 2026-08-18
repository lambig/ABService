package com.abservice.application.query.albumarticle;

import com.abservice.application.query.QueryService;

/**
 * アルバム記事一覧照会のクエリ（ページネーション付き）
 *
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 */
public record ListAlbumArticlesQuery(int page, int size) implements QueryService.Query {
}
