package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

/**
 * 記事非公開化コマンドの入力DTO
 *
 * @param articleId
 *            非公開化対象の記事ID
 */
public record UnpublishArticleInput(@Nullable String articleId) implements CommandService.Input {

    /**
     * 自身が妥当（{@code articleId}が有効な形式）であることを検証する
     *
     * <p>
     * 検証責務をCommandService側に持たせず、Input自身が答えられるようにする（#148で
     * {@link com.abservice.domain.model.policy.Policy}を用いた共通デフォルト実装へ移行予定）。
     * </p>
     *
     * @return 検証済みの自身。無効な場合は{@link ValidationException}で失敗する
     */
    Uni<UnpublishArticleInput> asValidated() {
        return Uni.createFrom()
                .item(
                        () -> Article.Id.fromInput(articleId)
                                .resolve(ValidationException::new))
                .replaceWith(this);
    }
}
