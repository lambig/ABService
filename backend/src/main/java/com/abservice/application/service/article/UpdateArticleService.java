package com.abservice.application.service.article;

import com.abservice.application.exception.ConflictingEditException;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.lib.Result;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

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
 * 値の検証は入力自身が担い、本サービスは対象の取得・状態確認・集約への反映と保存を担います。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class UpdateArticleService implements CommandService<UpdateArticleInput, UpdateArticleOutput> {

    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<UpdateArticleOutput> execute(UpdateArticleInput input) {
        return input.asRequested()
                .flatMap(this::findExisting)
                .map(claimed -> claimedAsOf(claimed, input))
                .flatMap(existing -> applyValidatedUpdate(existing, input))
                .flatMap(articleRepository::saveWithRevision)
                .map(UpdateArticleService::toOutput);
    }

    /**
     * 掴んだ行の世代が、編集を始めた時点と同じであることを確かめる。
     *
     * <p>
     * 違っていれば、この更新が持っている値は既に古い。届いた値を最新へ適用すると、間に入った保存を消すため拒む。
     * </p>
     */
    private static Article claimedAsOf(ArticleRepository.Revisioned claimed, UpdateArticleInput input) {
        return Objects.equals(
                claimed.revision()
                        .value(),
                input.expectedRevision())
                        ? claimed.article()
                        : conflicting(claimed);
    }

    private static Article conflicting(ArticleRepository.Revisioned claimed) {
        throw new ConflictingEditException(
                "記事 %s は編集を始めた後に更新されています".formatted(
                        claimed.article()
                                .id()
                                .value()));
    }

    private Uni<ArticleRepository.Revisioned> findExisting(Article.Id id) {
        return articleRepository.findByIdWithRevision(id)
                .onItem()
                .ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private Uni<Article> applyValidatedUpdate(Article existing, UpdateArticleInput input) {
        return businessDateTimeProvider.now()
                .call(input::asValidated)
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
        return input.bodyValues()
                .map(
                        values -> existing.changeArticleType(values.type(), now)
                                .changeTitle(values.title(), now)
                                .changeBody(values.body(), now)
                                .changeIntroShort(values.introShort(), now));
    }

    private static UpdateArticleOutput toOutput(ArticleRepository.Revisioned saved) {
        return new UpdateArticleOutput(
                saved.article()
                        .id()
                        .value(),
                saved.revision()
                        .value(),
                saved.article()
                        .articleType()
                        .name(),
                saved.article()
                        .title()
                        .value(),
                saved.article()
                        .isPublic());
    }
}
