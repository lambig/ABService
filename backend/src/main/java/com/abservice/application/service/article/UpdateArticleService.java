package com.abservice.application.service.article;

import com.abservice.application.exception.ConflictingEditException;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.IntroShort;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
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
                .item(() -> requested(input))
                .flatMap(requested -> findExisting(requested.articleId()))
                .map(claimed -> claimedAsOf(claimed, input))
                .flatMap(existing -> applyValidatedUpdate(existing, input))
                .flatMap(articleRepository::saveWithRevision)
                .map(UpdateArticleService::toOutput);
    }

    /** 更新の対象と条件。どちらも欠けていれば検証エラーで、記事を掴む前に決まる */
    private record Requested(Article.Id articleId, int expectedRevision) {
    }

    private static Requested requested(UpdateArticleInput input) {
        return Result.zip(
                Article.Id.fromInput(input.articleId())
                        .mapErrorFields(field -> "articleId"),
                expectedRevision(input.expectedRevision()),
                Requested::new)
                .resolve(ValidationException::new);
    }

    /**
     * 編集を始めた時点の世代。
     *
     * <p>
     * 未指定を「条件なし」として通さない。全項目置換のため、条件を持たない更新は、編集の間に入った別の保存を 黙って消す（DECISIONS 30）。
     * </p>
     */
    private static Result<Integer> expectedRevision(@Nullable Integer value) {
        return Optional.ofNullable(value)
                .map(Result::success)
                .orElseGet(
                        () -> Result.failure(
                                new ErrorResult(
                                        "expectedRevision",
                                        "編集を始めた時点の世代は必須です",
                                        "ARTICLE_EXPECTED_REVISION_REQUIRED")));
    }

    /**
     * 掴んだ行の世代が、編集を始めた時点と同じであることを確かめる。
     *
     * <p>
     * 違っていれば、この更新が持っている値は既に古い。届いた値を最新へ適用すると、間に入った保存を消すため拒む。
     * </p>
     */
    private static Article claimedAsOf(ArticleRepository.Revisioned claimed, UpdateArticleInput input) {
        return Objects.equals(claimed.revision().value(), input.expectedRevision())
                ? claimed.article()
                : conflicting(claimed);
    }

    private static Article conflicting(ArticleRepository.Revisioned claimed) {
        throw new ConflictingEditException(
                "記事 %s は編集を始めた後に更新されています".formatted(claimed.article().id().value()));
    }

    private Uni<ArticleRepository.Revisioned> findExisting(Article.Id id) {
        return articleRepository.findByIdWithRevision(id)
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
                IntroShort.fromInput(input.introShort()),
                (title, type, body, introShort) -> existing.changeArticleType(type, now)
                        .changeTitle(title, now)
                        .changeBody(body, now)
                        .changeIntroShort(introShort, now));
    }

    /** 本文なし（blank 入力）を表す検証結果。完全に使い回せる定数。 */
    private static final Result<MarkupContent> EMPTY_BODY = Result.success(MarkupContent.EMPTY);

    private static Result<MarkupContent> resolveBody(@Nullable String content, @Nullable String format) {
        return Optional.ofNullable(content)
                .filter(StringUtils::isNotBlank)
                .map(
                        c -> MarkupContent.fromInput(c, format)
                                .mapErrorFields(ArticleInputPaths::body))
                .orElse(EMPTY_BODY);
    }

    private static UpdateArticleOutput toOutput(ArticleRepository.Revisioned saved) {
        return new UpdateArticleOutput(
                saved.article().id().value(),
                saved.revision().value(),
                saved.article().articleType().name(),
                saved.article().title().value(),
                saved.article().isPublic());
    }
}
