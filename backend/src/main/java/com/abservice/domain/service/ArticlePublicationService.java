package com.abservice.domain.service;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.CrossAggregateOperation;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.Article;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 記事の公開を担うドメインサービス
 *
 * <p>
 * {@link Article} は {@link Album} をIDでしか参照しないため、公開してよいかを単体では判定できない。本サービスは
 * 参照先を引き、操作オブジェクト（{@link ArticlePublication}）を組み立てて実行する。
 * </p>
 *
 * <p>
 * 操作オブジェクトは永続化されず識別子も持たない、本サービスの中でだけ意味を持つモデルのためネスト型として置く。規則の評価
 * （{@code asValidated}）と、規則を満たすときだけの遷移を自身が持ち、判定はI/Oを伴わない純粋な評価になる。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ArticlePublicationService implements DomainService {

    private final AlbumAccessService albumAccessService;

    /**
     * 記事を公開する試み
     *
     * @param article
     *            公開対象の記事
     * @param referencedAlbum
     *            記事が参照しているアルバム（参照を持たない場合や参照先が引けなかった場合は null）
     */
    @CrossAggregateOperation
    public record ArticlePublication(Article article, @Nullable Album referencedAlbum) {

        /** 参照が失効している記事は公開できない */
        private static final ErrorResult REFERENCE_LOST_ERROR = new ErrorResult(
                "albumReference",
                "Cannot publish an article whose referenced album no longer exists",
                "ARTICLE_ALBUM_REFERENCE_LOST");

        /** 参照先アルバムが公開中でなければ記事を公開できない */
        private static final ErrorResult REFERENCED_ALBUM_NOT_PUBLISHED_ERROR = new ErrorResult(
                "albumReference",
                "Cannot publish an article referencing an album that is not published",
                "ARTICLE_REFERENCED_ALBUM_NOT_PUBLISHED");

        /**
         * 公開してよいかを評価します（例外を投げず、結果を {@link Result} で返す）。
         *
         * @return 公開してよければ自身の {@code Success}、規則を満たさなければ検証エラーの {@code Failure}
         */
        public Result<ArticlePublication> asValidated() {
            return policy().verify(this, Function.identity());
        }

        /**
         * 規則を満たすときだけ記事を公開します。
         *
         * @param currentDateTime
         *            現在日時
         * @return 公開された記事
         */
        public Article publish(BusinessDateTime currentDateTime) {
            return asValidated()
                    .map(validated -> validated.article().publish(currentDateTime))
                    .resolve(BusinessRuleViolationException::fromErrors);
        }

        private static Policy<ArticlePublication> policy() {
            return Policy.all(
                    Policy.of(
                            publication -> lostReference(publication).isEmpty(),
                            REFERENCE_LOST_ERROR),
                    Policy.of(
                            publication -> unpublishedReference(publication).isEmpty(),
                            REFERENCED_ALBUM_NOT_PUBLISHED_ERROR));
        }

        private static Optional<AlbumReference.Lost> lostReference(@Nullable ArticlePublication publication) {
            return Optional.ofNullable(publication)
                    .map(ArticlePublication::article)
                    .map(Article::albumReference)
                    .flatMap(AlbumReference::lost);
        }

        private static Optional<Album.Id> unpublishedReference(@Nullable ArticlePublication publication) {
            return Optional.ofNullable(publication)
                    .flatMap(ArticlePublication::referenceWithoutPublishedAlbum);
        }

        private Optional<Album.Id> referenceWithoutPublishedAlbum() {
            return article.albumReference().activeAlbumId()
                    .filter(referencedId -> publishedAlbum(referencedId).isEmpty());
        }

        private Optional<Album> publishedAlbum(Album.Id referencedId) {
            return Optional.ofNullable(referencedAlbum)
                    .filter(candidate -> candidate.id().equals(referencedId))
                    .filter(Album::isPublished);
        }
    }

    /**
     * 記事を公開します。
     *
     * @param article
     *            公開対象の記事
     * @param currentDateTime
     *            現在日時
     * @return 公開された記事。参照先が未存在なら {@code EntityNotFoundException}、規則を満たさない場合は
     *         {@link BusinessRuleViolationException} で失敗する
     */
    public Uni<Article> publish(Article article, BusinessDateTime currentDateTime) {
        return referencedAlbum(article)
                .map(album -> new ArticlePublication(article, album.orElse(null)))
                .map(publication -> publication.publish(currentDateTime));
    }

    private Uni<Optional<Album>> referencedAlbum(Article article) {
        return article.albumReference().activeAlbumId()
                .map(this::findExistingAsOptional)
                .orElseGet(() -> Uni.createFrom().item(Optional.empty()));
    }

    private Uni<Optional<Album>> findExistingAsOptional(Album.Id albumId) {
        return albumAccessService.findExistingAndClaimReference(albumId)
                .map(Optional::of);
    }
}
