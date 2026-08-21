package com.abservice.application.query.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;

/**
 * アルバム一覧照会のクエリ（ページネーション付き）
 *
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 * @param audience
 *            要求元（公開向けは公開中のみ、管理向けは下書きも対象）
 */
public record ListAlbumsQuery(int page, int size, Audience audience) implements QueryService.Query {
}
