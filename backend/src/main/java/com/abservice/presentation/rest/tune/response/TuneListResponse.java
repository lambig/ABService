package com.abservice.presentation.rest.tune.response;

import java.util.List;

/**
 * チューン一覧レスポンス（REST の公開出力契約）
 *
 * @param items
 *            このページ分のチューン詳細レスポンス
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 * @param totalElements
 *            全件数
 * @param totalPages
 *            総ページ数
 */
public record TuneListResponse(
        List<TuneResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
