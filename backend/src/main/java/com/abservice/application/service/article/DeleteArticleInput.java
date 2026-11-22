package com.abservice.application.service.article;

import com.abservice.lib.Result;
import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.article.Article;
import org.jspecify.annotations.Nullable;

/**
 * 記事削除コマンドの入力DTO
 *
 * @param articleId
 *            削除対象の記事ID
 */
public record DeleteArticleInput(
        @Nullable String articleId) implements CommandService.Input<DeleteArticleInput> {

    @Override
    public Policy<DeleteArticleInput> policy() {
        return new InputPolicy<>(DeleteArticleInput::parsed);
    }

    Result<Article.Id> parsed() {
        return Article.Id.fromInput(articleId())
                .mapErrorFields(field -> "articleId");
    }
}
