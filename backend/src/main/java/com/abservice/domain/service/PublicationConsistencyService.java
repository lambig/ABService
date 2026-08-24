package com.abservice.domain.service;

import static java.util.function.Predicate.not;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.vo.article.AlbumReference;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import lombok.AllArgsConstructor;

/**
 * 記事とアルバムの公開整合を扱うドメインサービス
 *
 * <p>
 * 「公開中の記事は、存在して公開中のアルバムだけを参照する」という集約をまたぐ不変条件を集約する。Article 単体でも Album
 * 単体でも判定できないため、参照側・被参照側の両方を見る本サービスが唯一の判定箇所になる。
 * </p>
 *
 * <p>
 * 不整合を作る方向（前進方向）は禁止し、既存の整合を崩す方向（アルバムの非公開化・削除）はカスケードで記事側を追随させる。
 * 後者はユースケース（{@code UnpublishAlbumService} / {@code DeleteAlbumService}）が担う。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class PublicationConsistencyService implements DomainService {

    private final AlbumExistenceService albumExistenceService;

    /**
     * 記事を公開してよいことを確認します。
     *
     * <p>
     * 参照が有効なら参照先アルバムが存在して公開中であること、参照が失効しているなら公開できないことを判定します。参照を持たない 記事は制約を受けません。
     * </p>
     *
     * @param article
     *            公開しようとしている記事
     * @return 公開してよい記事。参照先が未存在なら {@code EntityNotFoundException}、公開できない状態なら
     *         {@link BusinessRuleViolationException} で失敗する
     */
    public Uni<Article> requirePublishable(Article article) {
        return switch (article.albumReference()) {
            case AlbumReference.Referenced referenced -> requirePublishedAlbum(referenced.albumId())
                    .replaceWith(article);
            case AlbumReference.Lost lost -> Uni.createFrom().failure(() -> albumReferenceLost(lost));
            case AlbumReference.None ignored -> Uni.createFrom().item(article);
        };
    }

    /**
     * 記事にアルバムを紐付けてよいことを確認します。
     *
     * <p>
     * 参照先アルバムが存在することに加え、公開中の記事には公開中のアルバムしか紐付けられません（公開中の記事が非公開アルバムを
     * 参照する不整合を、紐付けの経路からも作らせない）。
     * </p>
     *
     * @param article
     *            紐付け対象の記事
     * @param albumId
     *            紐付けようとしているアルバムのID
     * @return 紐付けてよいアルバム。未存在なら {@code EntityNotFoundException}、公開中の記事に非公開アルバムを
     *         紐付けようとした場合は {@link BusinessRuleViolationException} で失敗する
     */
    public Uni<Album> requireAttachable(Article article, Album.Id albumId) {
        return albumExistenceService.findExisting(albumId)
                .flatMap(album -> requireConsistent(article, album));
    }

    private Uni<Album> requirePublishedAlbum(Album.Id albumId) {
        return albumExistenceService.findExisting(albumId)
                .flatMap(PublicationConsistencyService::requirePublished);
    }

    private static Uni<Album> requirePublished(Album album) {
        return unpublished(album)
                .map(PublicationConsistencyService::failWithUnpublishedAlbum)
                .orElseGet(() -> Uni.createFrom().item(album));
    }

    private static Uni<Album> requireConsistent(Article article, Album album) {
        return published(article)
                .map(published -> requirePublishedFor(published, album))
                .orElseGet(() -> Uni.createFrom().item(album));
    }

    private static Uni<Album> requirePublishedFor(Article publishedArticle, Album album) {
        return unpublished(album)
                .map(unpublishedAlbum -> failWithUnpublishedAlbumFor(publishedArticle, unpublishedAlbum))
                .orElseGet(() -> Uni.createFrom().item(album));
    }

    private static Optional<Article> published(Article article) {
        return Optional.of(article)
                .filter(Article::isPublic);
    }

    private static Optional<Album> unpublished(Album album) {
        return Optional.of(album)
                .filter(not(Album::isPublished));
    }

    private static Uni<Album> failWithUnpublishedAlbum(Album album) {
        return Uni.createFrom()
                .failure(
                        new BusinessRuleViolationException(
                                "参照先のアルバム（%s）が非公開です".formatted(album.id().value())));
    }

    private static Uni<Album> failWithUnpublishedAlbumFor(Article publishedArticle, Album album) {
        return Uni.createFrom()
                .failure(
                        new BusinessRuleViolationException(
                                "公開中の記事（%s）には非公開のアルバム（%s）を紐付けられません"
                                        .formatted(
                                                publishedArticle.id().value(),
                                                album.id().value())));
    }

    private static BusinessRuleViolationException albumReferenceLost(AlbumReference.Lost lost) {
        return new BusinessRuleViolationException(
                "参照先のアルバム（%s）が存在しないため公開できません".formatted(lost.formerAlbumId().value()));
    }
}
