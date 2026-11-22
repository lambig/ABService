package com.abservice.application.service.article;

import com.abservice.lib.Result;
import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.article.Article;
import org.jspecify.annotations.Nullable;

/**
 * 記事公開コマンドの入力DTO
 *
 * @param articleId
 *            公開対象の記事ID
 */
public record PublishArticleInput(
        @Nullable String articleId) implements CommandService.Input<PublishArticleInput> {

    @Override
    public Policy<PublishArticleInput> policy() {
        return new InputPolicy<>(PublishArticleInput::parsed);
    }

    Result<Article.Id> parsed() {
        return Article.Id.fromInput(articleId())
                .mapErrorFields(field -> "articleId");
    }
}
