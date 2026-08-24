package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.AlbumExistenceService;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.AllArgsConstructor;

/**
 * 記事公開コマンドサービス
 *
 * <p>
 * {@link Article#publish(com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。対象記事がアルバム記事（{@code albumId}が非null）の場合、
 * {@link AlbumExistenceService#findPublic(Album.Id)} を介して参照先の {@link Album}
 * が公開中であることを確認してから公開します（非公開Albumを参照する記事は公開できない、
 * 集約をまたぐ不変条件のためドメインサービスで検証）。未存在は{@link EntityNotFoundException}（404）、
 * 非公開は{@link BusinessRuleViolationException}（409）とします。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class PublishArticleService implements CommandService<PublishArticleInput, PublishArticleOutput> {

    private final ArticleRepository articleRepository;
    private final AlbumExistenceService albumExistenceService;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<PublishArticleOutput> execute(PublishArticleInput input) {
        return input.asValidated()
                .map(valid -> Article.Id.of(Objects.requireNonNull(valid.articleId())))
                .flatMap(this::findExisting)
                .flatMap(this::verifyReferencedAlbumIsPublished)
                .flatMap(
                        existing -> businessDateTimeProvider.now()
                                .map(existing::publish))
                .flatMap(articleRepository::save)
                .map(PublishArticleService::toOutput);
    }

    private Uni<Article> findExisting(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()));
    }

    private Uni<Article> verifyReferencedAlbumIsPublished(Article article) {
        return switch (article.albumReference()) {
            case AlbumReference.Referenced referenced -> albumExistenceService.findPublic(referenced.albumId())
                    .replaceWith(article);
            case AlbumReference.Lost lost -> Uni.createFrom()
                    .failure(() -> albumReferenceLost(lost));
            case AlbumReference.None ignored -> Uni.createFrom().item(article);
        };
    }

    private static BusinessRuleViolationException albumReferenceLost(AlbumReference.Lost lost) {
        return new BusinessRuleViolationException(
                "Cannot publish an article whose referenced album (%s) no longer exists"
                        .formatted(lost.formerAlbumId().value()));
    }

    private static PublishArticleOutput toOutput(Article article) {
        return new PublishArticleOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value(),
                article.isPublic());
    }
}
