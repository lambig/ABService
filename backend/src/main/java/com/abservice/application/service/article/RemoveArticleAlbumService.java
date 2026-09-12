package com.abservice.application.service.article;

import com.abservice.application.exception.ConflictingEditException;
import com.abservice.application.exception.Failure;
import com.abservice.application.exception.FailureContract;
import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * 記事のAlbum参照解除コマンドサービス
 *
 * <p>
 * {@link AlbumArticle#detachAlbum(com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。紐付け（{@link SetArticleAlbumService}）と違い、解除の可否は参照先アルバムの状態に
 * 依存しないため、ドメインサービスを経由せず記事だけで完結します（公開中の記事に非公開のアルバムを紐付けられないという
 * 規則は、参照を外す側には働きません）。
 * </p>
 *
 * <p>
 * 参照を持たない記事に対しても、失効した参照を持つ記事に対してもべき等に成功します（記事から見た結果が同じため）。
 * 対象記事の種別が{@link ArticleType#ALBUM}でない場合は、参照という概念自体を持たないため
 * {@link BusinessRuleViolationException}（409）とします。
 * </p>
 *
 * <p>
 * 解除もArticleの世代を進めるため、{@link SetArticleAlbumService}と同じ{@code expectedRevision}契約を
 * 適用します（#323）。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
@FailureContract({Failure.VALIDATION, Failure.NOT_FOUND, Failure.CONFLICT})
public class RemoveArticleAlbumService implements CommandService<RemoveArticleAlbumInput, RemoveArticleAlbumOutput> {

    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<RemoveArticleAlbumOutput> execute(RemoveArticleAlbumInput input) {
        return input.asValidated()
                .flatMap(
                        valid -> findExisting(toArticleId(valid))
                                .map(claimed -> claimedAsOf(claimed, valid))
                                .flatMap(RemoveArticleAlbumService::requireAlbumType)
                                .flatMap(this::detached)
                                .flatMap(articleRepository::saveWithRevision)
                                .map(RemoveArticleAlbumService::toOutput));
    }

    private static Article.Id toArticleId(RemoveArticleAlbumInput valid) {
        return Article.Id.of(Objects.requireNonNull(valid.articleId()));
    }

    private Uni<ArticleRepository.Revisioned> findExisting(Article.Id id) {
        return articleRepository.findByIdWithRevision(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    /**
     * 掴んだ行の世代が、編集を始めた時点と同じであることを確かめる。
     *
     * <p>
     * 違っていれば、この解除が持っている値は既に古い。届いた値を最新へ適用すると、間に入った保存を消すため拒む。
     * </p>
     */
    private static Article claimedAsOf(ArticleRepository.Revisioned claimed, RemoveArticleAlbumInput input) {
        return Objects.equals(claimed.revision().value(), input.expectedRevision())
                ? claimed.article()
                : conflicting(claimed);
    }

    private static Article conflicting(ArticleRepository.Revisioned claimed) {
        throw new ConflictingEditException(
                "記事 %s は編集を始めた後に更新されています".formatted(claimed.article().id().value()));
    }

    private Uni<AlbumArticle> detached(AlbumArticle article) {
        return businessDateTimeProvider.now()
                .map(article::detachAlbum);
    }

    /*
     * NARROWING: アルバムを参照できるのは AlbumArticle だけで、他の種別は参照という概念自体を持たない。
     * 型で絞れなかった場合を業務違反として返す。
     */
    private static Uni<AlbumArticle> requireAlbumType(Article article) {
        return AlbumArticle.from(article)
                .map(Uni.createFrom()::item)
                .orElseGet(
                        () -> Uni.createFrom()
                                .failure(
                                        new BusinessRuleViolationException(
                                                "ALBUM種別の記事のみアルバムの紐付けを解除できます")));
    }

    private static RemoveArticleAlbumOutput toOutput(ArticleRepository.Revisioned saved) {
        return new RemoveArticleAlbumOutput(
                saved.article().id().value(),
                saved.revision().value(),
                saved.article().articleType().name(),
                saved.article().title().value());
    }
}
