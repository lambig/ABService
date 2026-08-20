package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import org.jspecify.annotations.Nullable;

/**
 * 記事公開コマンドの入力DTO
 *
 * @param articleId
 *            公開対象の記事ID
 */
public record PublishArticleInput(@Nullable String articleId) implements CommandService.Input {
}
