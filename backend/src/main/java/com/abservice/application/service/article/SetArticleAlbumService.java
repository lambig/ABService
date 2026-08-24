package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import com.abservice.domain.service.PublicationConsistencyService;
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
 * を呼び出すユースケースです。紐付けてよいかどうかの判定（参照先の存在と、公開中の記事に対する参照先の公開状態）は集約をまたぐ
 * 不変条件のため{@link PublicationConsistencyService}に委ねます。対象記事の種別が{@link ArticleType#ALBUM}
 * でない場合は記事単体で決まる制約のため本サービスで{@link BusinessRuleViolationException}（409）とします。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class SetArticleAlbumService implements CommandService<SetArticleAlbumInput, SetArticleAlbumOutput> {

    private final ArticleRepository articleRepository;
    private final PublicationConsistencyService publicationConsistencyService;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<SetArticleAlbumOutput> execute(SetArticleAlbumInput input) {
        return input.asValidated()
                .map(SetArticleAlbumService::toIds)
                .flatMap(
                        ids -> findExistingAlbumArticle(ids.articleId())
                                .flatMap(
                                        article -> publicationConsistencyService
                                                .requireAttachable(article, ids.albumId())
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
