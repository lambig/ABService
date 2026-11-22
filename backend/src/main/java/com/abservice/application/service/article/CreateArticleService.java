package com.abservice.application.service.article;

import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;

/**
 * 記事作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateArticleInput}）から新規 {@link Article} を生成して永続化するユースケースです。
 * </p>
 *
 * <p>
 * 値の検証は入力自身が担い、本サービスは検証後の値から集約を作成して保存します。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract(Failure.VALIDATION)
public class CreateArticleService implements CommandService<CreateArticleInput, CreateArticleOutput> {

    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<CreateArticleOutput> execute(CreateArticleInput input) {
        return businessDateTimeProvider.now()
                .call(input::asValidated)
                .map(
                        now -> validate(input, now)
                                .resolve(ValidationException::new))
                .flatMap(articleRepository::save)
                .map(CreateArticleService::toOutput);
    }

    static Result<Article> validate(CreateArticleInput input, BusinessDateTime now) {
        return input.parsed()
                .map(
                        values -> Article.create(
                                values.type(),
                                null,
                                values.title(),
                                values.body(),
                                values.introShort(),
                                now));
    }

    private static CreateArticleOutput toOutput(Article article) {
        return new CreateArticleOutput(
                article.id()
                        .value(),
                article.articleType()
                        .name(),
                article.title()
                        .value(),
                article.isPublic());
    }
}
