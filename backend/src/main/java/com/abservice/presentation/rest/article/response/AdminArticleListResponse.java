package com.abservice.presentation.rest.article.response;

import java.util.List;

/**
 * 管理向け記事一覧レスポンス（REST の公開出力契約）
 *
 * @param items
 *            このページ分の記事（一覧表示用の項目のみ）
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 * @param totalElements
 *            全件数
 * @param totalPages
 *            総ページ数
 */
public record AdminArticleListResponse(
        List<AdminArticleResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
