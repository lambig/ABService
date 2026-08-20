package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 記事非公開化コマンドの入力DTO
 *
 * @param articleId
 *            非公開化対象の記事ID
 */
public record UnpublishArticleInput(@Nullable String articleId) implements CommandService.Input {
}
