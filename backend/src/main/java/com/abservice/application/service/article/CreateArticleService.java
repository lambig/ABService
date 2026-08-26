package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * 記事作成コマンドサービス
 *
 * <p>
 * 外部入力（{@link CreateArticleInput}）から新規 {@link Article} を生成して永続化するユースケースです。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して {@code Article} を組み立てるオーケストレーションに徹します。検証失敗は
 * {@link ValidationException} に集約し、HTTP への変換は presentation 層の ExceptionMapper
 * が担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class CreateArticleService implements CommandService<CreateArticleInput, CreateArticleOutput> {

    private final ArticleRepository articleRepository;

    @WithTransaction
    @Override
    public Uni<CreateArticleOutput> execute(CreateArticleInput input) {
        return Uni.createFrom()
                .item(
                        () -> validate(input)
                                .resolve(ValidationException::new))
                .flatMap(articleRepository::save)
                .map(CreateArticleService::toOutput);
    }

    static Result<Article> validate(CreateArticleInput input) {
        return Result.zip(
                ArticleTitle.fromInput(input.title()),
                ArticleType.fromInput(input.articleType()),
                resolveBody(input.body(), input.bodyFormat()),
                (title, type, body) -> Article.create(
                        type,
                        null,
                        title,
                        body,
                        input.introShort()));
    }

    /** 本文なし（blank 入力）を表す検証結果。完全に使い回せる定数。 */
    private static final Result<MarkupContent> EMPTY_BODY = Result.success(MarkupContent.EMPTY);

    private static Result<MarkupContent> resolveBody(@Nullable String content, @Nullable String format) {
        return Optional.ofNullable(content)
                .filter(StringUtils::isNotBlank)
                .map(c -> MarkupContent.fromInput(c, format))
                .orElse(EMPTY_BODY);
    }

    private static CreateArticleOutput toOutput(Article article) {
        return new CreateArticleOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value(),
                article.isPublic());
    }
}
