package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.MarkupContent;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * 記事更新コマンドサービス
 *
 * <p>
 * 外部入力（{@link UpdateArticleInput}）から既存 {@link Article} のCreate相当フィールド
 * （articleType/title/body/introShort）をPUT風に全項目置換するユースケースです。公開状態とタグは
 * 対象外のため既存の値をそのまま維持します。
 * </p>
 *
 * <p>
 * 値検証はドメインの各値オブジェクトの {@code fromInput}（{@code Result} 返却）に委譲し、本サービスはそれらを
 * {@link Result#zip} で集約して既存 {@code Article} を更新後の状態へ組み替えるオーケストレーションに徹します。検証失敗は
 * {@link ValidationException} に、対象記事の不在は {@link EntityNotFoundException}
 * に集約し、HTTP への変換は presentation 層の ExceptionMapper が担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class UpdateArticleService implements CommandService<UpdateArticleInput, UpdateArticleOutput> {

    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<UpdateArticleOutput> execute(UpdateArticleInput input) {
        return Uni.createFrom()
                .item(
                        () -> Article.Id.fromInput(input.articleId())
                                .resolve(ValidationException::new))
                .flatMap(this::findExisting)
                .flatMap(existing -> applyValidatedUpdate(existing, input))
                .flatMap(articleRepository::save)
                .map(UpdateArticleService::toOutput);
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private Uni<Article> applyValidatedUpdate(Article existing, UpdateArticleInput input) {
        return businessDateTimeProvider.now()
                .map(
                        now -> validateAndApply(
                                existing,
                                input,
                                now)
                                .resolve(ValidationException::new));
    }

    static Result<Article> validateAndApply(
            Article existing,
            UpdateArticleInput input,
            BusinessDateTime now) {
        return Result.zip(
                ArticleTitle.fromInput(input.title()),
                ArticleType.fromInput(input.articleType()),
                resolveBody(input.body(), input.bodyFormat()),
                (title, type, body) -> existing.changeArticleType(type, now)
                        .changeTitle(title, now)
                        .changeBody(body, now)
                        .changeIntroShort(input.introShort(), now));
    }

    /** 本文なし（blank 入力）を表す検証結果。完全に使い回せる定数。 */
    private static final Result<MarkupContent> EMPTY_BODY = Result.success(MarkupContent.EMPTY);

    private static Result<MarkupContent> resolveBody(@Nullable String content, @Nullable String format) {
        return Optional.ofNullable(content)
                .filter(StringUtils::isNotBlank)
                .map(c -> MarkupContent.fromInput(c, format))
                .orElse(EMPTY_BODY);
    }

    private static UpdateArticleOutput toOutput(Article article) {
        return new UpdateArticleOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value(),
                article.isPublic());
    }
}
