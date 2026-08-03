package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 記事削除コマンドの入力DTO
 *
 * @param articleId
 *            削除対象の記事ID
 */
public record DeleteArticleInput(@Nullable String articleId) implements CommandService.Input {
}
