package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 記事タグ追加の入力
 *
 * <p>
 * タグは名前で指定する。同じ名前のタグが既にあればそれを使い、無ければ作る（{@link AddArticleTagService}）。
 * </p>
 *
 * @param articleId
 *            対象記事のドメインID
 * @param name
 *            タグ名（nullable。未検証の外部入力）
 */
public record AddArticleTagInput(String articleId, @Nullable String name) implements CommandService.Input {
}
