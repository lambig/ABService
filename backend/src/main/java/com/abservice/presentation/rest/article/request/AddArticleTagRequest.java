package com.abservice.presentation.rest.article.request;

import org.jspecify.annotations.Nullable;

/**
 * 記事タグ追加リクエスト
 *
 * <p>
 * タグは名前で指定する。同じ名前のタグが既にあればそれが付き、無ければ作られる。
 * </p>
 *
 * @param name
 *            タグ名（必須。100文字以内）
 */
public record AddArticleTagRequest(@Nullable String name) {
}
