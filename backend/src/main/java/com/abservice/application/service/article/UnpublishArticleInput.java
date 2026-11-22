package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.article.Article;
import org.jspecify.annotations.Nullable;

/**
 * 記事非公開化コマンドの入力DTO
 *
 * @param articleId
 *            非公開化対象の記事ID
 */
public record UnpublishArticleInput(
        @Nullable String articleId) implements CommandService.Input<UnpublishArticleInput> {

    @Override
    public Policy<UnpublishArticleInput> policy() {
        return new InputPolicy<>(input -> Article.Id.fromInput(input.articleId())
                .mapErrorFields(field -> "articleId"));
    }
}
