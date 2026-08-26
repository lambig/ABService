package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;
import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.repository.article.ArticleRepository;
import com.abservice.domain.service.ArticleAlbumAttachmentService;
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
 * を呼び出すユースケースです。紐付けという操作そのものは{@link ArticleAlbumAttachmentService}が担います（参照先アルバムの
 * 状態に依存するため記事単体では可否を判定できない）。対象記事の種別が{@link ArticleType#ALBUM}でない場合は記事単体で
 * 決まる制約のため本サービスで{@link BusinessRuleViolationException}（409）とします。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class SetArticleAlbumService implements CommandService<SetArticleAlbumInput, SetArticleAlbumOutput> {

    private final ArticleRepository articleRepository;
    private final ArticleAlbumAttachmentService articleAlbumAttachmentService;
    private final BusinessDateTimeProvider businessDateTimeProvider;

    @WithTransaction
    @Override
    public Uni<SetArticleAlbumOutput> execute(SetArticleAlbumInput input) {
        return input.asValidated()
                .map(SetArticleAlbumService::toIds)
                .flatMap(
                        ids -> findExistingAlbumArticle(ids.articleId())
                                .flatMap(article -> attachAlbum(article, ids.albumId()))
                                .flatMap(articleRepository::save)
                                .map(saved -> toOutput(saved, ids.albumId())));
    }

    private Uni<AlbumArticle> attachAlbum(AlbumArticle article, Album.Id albumId) {
        return businessDateTimeProvider.now()
                .flatMap(
                        now -> articleAlbumAttachmentService.attachAlbum(
                                article,
                                albumId,
                                now));
    }

    private record Ids(Article.Id articleId, Album.Id albumId) {
    }

    private static Ids toIds(SetArticleAlbumInput valid) {
        return new Ids(
                Article.Id.of(Objects.requireNonNull(valid.articleId())),
                Album.Id.of(Objects.requireNonNull(valid.albumId())));
    }

    private Uni<AlbumArticle> findExistingAlbumArticle(Article.Id id) {
        return articleRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> EntityNotFoundException.of("Article", id.value()))
                .flatMap(SetArticleAlbumService::requireAlbumType);
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
                                                "ALBUM種別の記事のみアルバムを紐付けられます")));
    }

    private static SetArticleAlbumOutput toOutput(Article article, Album.Id albumId) {
        return new SetArticleAlbumOutput(
                article.id().value(),
                article.articleType().name(),
                albumId.value(),
                article.title().value());
    }
}
