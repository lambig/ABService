package com.abservice.domain.model.aggregate.article;

import static java.util.function.Predicate.not;

import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.lib.ErrorResult;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 記事とアルバムの公開整合の規則
 *
 * <p>
 * 「公開中の記事は、存在して公開中のアルバムだけを参照する」という不変条件を、値だけで評価できる述語として表す。 {@link Article} は
 * {@link Album} をIDでしか参照しないため単体では判定できない。参照先を引く責務はドメインサービスが持ち、
 * 引いた結果を本ポリシーへ渡すことで、判定自体はI/Oを伴わない純粋な評価になる。
 * </p>
 */
public final class ArticlePublicationPolicy {

    private ArticlePublicationPolicy() {
    }

    /**
     * 公開しようとしている記事と、その参照先アルバム
     *
     * @param article
     *            公開対象の記事
     * @param referencedAlbum
     *            記事が参照しているアルバム（参照を持たない場合や参照先が引けなかった場合は null）
     */
    public record PublicationTarget(Article article, @Nullable Album referencedAlbum) {
    }

    /**
     * アルバムを紐付けようとしている記事と、紐付け先アルバム
     *
     * @param article
     *            紐付け対象の記事
     * @param album
     *            紐付け先のアルバム
     */
    public record AttachmentTarget(Article article, Album album) {
    }

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

    /** 公開中の記事には非公開のアルバムを紐付けられない */
    private static final ErrorResult UNPUBLISHED_ALBUM_ATTACHMENT_ERROR = new ErrorResult(
            "albumId",
            "Cannot attach an unpublished album to a published article",
            "ARTICLE_PUBLISHED_ALBUM_NOT_PUBLISHED");

    /**
     * 記事を公開してよいかの規則を返します。
     *
     * @return 公開可否のポリシー
     */
    public static Policy<PublicationTarget> publishable() {
        return Policy.all(
                Policy.of(
                        target -> lostReference(target).isEmpty(),
                        REFERENCE_LOST_ERROR),
                Policy.of(
                        target -> unpublishedReference(target).isEmpty(),
                        REFERENCED_ALBUM_NOT_PUBLISHED_ERROR));
    }

    /**
     * 記事にアルバムを紐付けてよいかの規則を返します。
     *
     * @return 紐付け可否のポリシー
     */
    public static Policy<AttachmentTarget> attachable() {
        return Policy.of(
                target -> unpublishedAttachment(target).isEmpty(),
                UNPUBLISHED_ALBUM_ATTACHMENT_ERROR);
    }

    private static Optional<AlbumReference.Lost> lostReference(@Nullable PublicationTarget target) {
        return Optional.ofNullable(target)
                .map(PublicationTarget::article)
                .map(Article::albumReference)
                .flatMap(AlbumReference::lost);
    }

    private static Optional<Album.Id> unpublishedReference(@Nullable PublicationTarget target) {
        return Optional.ofNullable(target)
                .flatMap(ArticlePublicationPolicy::referenceWithoutPublishedAlbum);
    }

    private static Optional<Album.Id> referenceWithoutPublishedAlbum(PublicationTarget target) {
        return target.article().albumReference().activeAlbumId()
                .filter(referencedId -> publishedAlbum(target.referencedAlbum(), referencedId).isEmpty());
    }

    private static Optional<Album> publishedAlbum(@Nullable Album album, Album.Id referencedId) {
        return Optional.ofNullable(album)
                .filter(candidate -> candidate.id().equals(referencedId))
                .filter(Album::isPublished);
    }

    private static Optional<Album> unpublishedAttachment(@Nullable AttachmentTarget target) {
        return Optional.ofNullable(target)
                .filter(candidate -> candidate.article().isPublic())
                .map(AttachmentTarget::album)
                .filter(not(Album::isPublished));
    }
}
