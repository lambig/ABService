package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.policy.Policies;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;

/**
 * 記事タグ削除の入力
 *
 * @param articleId
 *            対象記事のドメインID
 * @param tagId
 *            外すタグのドメインID
 */
public record RemoveArticleTagInput(String articleId,
        String tagId) implements CommandService.Input<RemoveArticleTagInput> {
    @Override
    public Policy<RemoveArticleTagInput> policy() {
        return new InputPolicy<>(input -> Policies.multiple(
                Article.Id.fromInput(input.articleId())
                        .withErrorField("articleId"),
                ArticleTag.Id.fromInput(input.tagId())
                        .withErrorField("tagId")));
    }
}
