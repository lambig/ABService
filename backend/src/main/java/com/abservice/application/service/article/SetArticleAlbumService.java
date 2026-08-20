package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.AlbumExistenceService;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * 記事へのAlbum参照設定（紐付け）コマンドサービス
 *
 * <p>
 * {@link Article#setAlbumId(Album.Id, com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。参照先の{@link Album}が存在することを{@link AlbumExistenceService}
 * で確認してから紐付けます（Album公開状態は問わない。公開時の制約は{@code PublishArticleService}が担う）。
 * 対象記事の種別が{@link ArticleType#ALBUM}でない場合は{@link BusinessRuleViolationException}（409）とします。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class SetArticleAlbumService implements CommandService<SetArticleAlbumInput, SetArticleAlbumOutput> {

    private final ArticleRepository articleRepository;
    private final AlbumExistenceService albumExistenceService;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<SetArticleAlbumOutput> execute(SetArticleAlbumInput input) {
        return input.asValidated()
                .map(SetArticleAlbumService::toIds)
                .flatMap(
                        ids -> findExistingAlbumArticle(ids.articleId())
                                .flatMap(
                                        article -> albumExistenceService.findExisting(ids.albumId())
                                                .replaceWith(businessDateTimeProvider.now())
                                                .map(now -> article.setAlbumId(ids.albumId(), now)))
                                .flatMap(articleRepository::save)
                                .map(saved -> toOutput(saved, ids.albumId())));
    }

    private record Ids(Article.Id articleId, Album.Id albumId) {
    }

    private static Ids toIds(SetArticleAlbumInput valid) {
        return new Ids(
                Article.Id.of(Objects.requireNonNull(valid.articleId())),
                Album.Id.of(Objects.requireNonNull(valid.albumId())));
    }

    private Uni<Article> findExistingAlbumArticle(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()))
                .flatMap(SetArticleAlbumService::requireAlbumType);
    }

    private static Uni<Article> requireAlbumType(Article article) {
        return article.articleType() == ArticleType.ALBUM
                ? Uni.createFrom().item(article)
                : Uni.createFrom()
                        .failure(
                                new BusinessRuleViolationException(
                                        "ALBUM種別の記事のみアルバムを紐付けられます"));
    }

    private static SetArticleAlbumOutput toOutput(Article article, Album.Id albumId) {
        return new SetArticleAlbumOutput(
                article.id().value(),
                article.articleType().name(),
                albumId.value(),
                article.title().value());
    }
}
