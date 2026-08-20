package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.repository.album.AlbumRepository;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.BusinessDateTimeProvider;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * 記事公開コマンドサービス
 *
 * <p>
 * {@link Article#publish(com.abservice.domain.model.vo.common.BusinessDateTime)}
 * を呼び出すユースケースです。対象記事がアルバム記事（{@code albumId}が非null）の場合、参照先の {@link Album}
 * が公開中であることを確認してから公開します（非公開Albumを参照する記事は公開できない、
 * 集約をまたぐ不変条件のためアプリケーション層で検証）。違反時は{@link BusinessRuleViolationException}（409）とします。
 * </p>
 */
@ApplicationScoped
public class PublishArticleService implements CommandService<PublishArticleInput, PublishArticleOutput> {

    private final ArticleRepository articleRepository;
    private final AlbumRepository albumRepository;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    /**
     * @param articleRepository
     *            記事リポジトリ
     * @param albumRepository
     *            アルバムリポジトリ（参照先の公開状態確認用）
     * @param businessDateTimeProvider
     *            ビジネス日時プロバイダー
     */
    public PublishArticleService(
            ArticleRepository articleRepository,
            AlbumRepository albumRepository,
            BusinessDateTimeProvider businessDateTimeProvider) {
        this.articleRepository = articleRepository;
        this.albumRepository = albumRepository;
        this.businessDateTimeProvider = businessDateTimeProvider;
    }

    @WithTransaction
    @Override
    public Uni<PublishArticleOutput> execute(PublishArticleInput input) {
        return Uni.createFrom()
                .item(
                        () -> Article.Id.fromInput(input.articleId())
                                .resolve(ValidationException::new))
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
        return Optional.ofNullable(article.albumId())
                .map(
                        albumId -> verifyAlbumPublished(albumId)
                                .replaceWith(article))
                .orElseGet(() -> Uni.createFrom().item(article));
    }

    private Uni<Void> verifyAlbumPublished(Album.Id albumId) {
        return albumRepository.findById(albumId)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Album", albumId.value()))
                .flatMap(PublishArticleService::requirePublished);
    }

    private static Uni<Void> requirePublished(Album album) {
        return album.isPublished()
                ? Uni.createFrom().voidItem()
                : Uni.createFrom()
                        .failure(
                                new BusinessRuleViolationException(
                                        "参照先のアルバムが非公開のため記事を公開できません"));
    }

    private static PublishArticleOutput toOutput(Article article) {
        return new PublishArticleOutput(
                article.id().value(),
                article.articleType().name(),
                article.title().value(),
                article.isPublic());
    }
}
