package com.abservice.presentation.rest.article.response;

import java.util.List;

/**
 * 管理向けタグ一覧レスポンス（REST の公開出力契約）
 *
 * <p>
 * ページングを持たない。タグは記事にタグを付けるときの選択肢であり、全件を一度に返す（`ListArticleTagsService`）。
 * </p>
 *
 * @param items
 *            タグの一覧（名前の昇順）
 */
public record AdminArticleTagListResponse(List<AdminArticleTagResponse> items) {
}
