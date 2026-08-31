package com.abservice.application.service.article;

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
 */
@ApplicationScoped
@AllArgsConstructor
public class RemoveArticleAlbumService implements CommandService<RemoveArticleAlbumInput, RemoveArticleAlbumOutput> {

    private final ArticleRepository articleRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<RemoveArticleAlbumOutput> execute(RemoveArticleAlbumInput input) {
        return input.asValidated()
                .map(RemoveArticleAlbumService::toArticleId)
                .flatMap(
                        articleId -> findExistingAlbumArticle(articleId)
                                .flatMap(this::detached)
                                .flatMap(articleRepository::save)
                                .map(RemoveArticleAlbumService::toOutput));
    }

    private static Article.Id toArticleId(RemoveArticleAlbumInput valid) {
        return Article.Id.of(Objects.requireNonNull(valid.articleId()));
    }

    private Uni<AlbumArticle> detached(AlbumArticle article) {
        return businessDateTimeProvider.now()
                .map(article::detachAlbum);
    }

    private Uni<AlbumArticle> findExistingAlbumArticle(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()))
                .flatMap(RemoveArticleAlbumService::requireAlbumType);
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

    private static RemoveArticleAlbumOutput toOutput(Article article) {
        return new RemoveArticleAlbumOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value());
    }
}
