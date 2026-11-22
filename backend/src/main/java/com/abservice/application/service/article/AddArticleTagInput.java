package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.application.service.InputPolicy;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.policy.Policies;
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
public record AddArticleTagInput(String articleId,
        @Nullable String name) implements CommandService.Input<AddArticleTagInput> {
    @Override
    public Policy<AddArticleTagInput> policy() {
        return new InputPolicy<>(input -> Policies.multiple(
                Article.Id.fromInput(input.articleId())
                        .withErrorField("articleId"),
                ArticleTag.namePolicy()
                        .check(input.name())));
    }
}
