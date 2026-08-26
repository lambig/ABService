package com.abservice.domain.service;

import static java.util.function.Predicate.not;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.CrossAggregateOperation;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.aggregate.article.AlbumArticle;
import com.abservice.domain.model.policy.Policy;
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
 * 記事へのアルバム紐付けを担うドメインサービス
 *
 * <p>
 * 紐付けてよいかは紐付け先 {@link Album} の公開状態に依存し、{@link Article} 単体では判定できない。本サービスは
 * 紐付け先を引き、操作オブジェクト（{@link AlbumAttachment}）を組み立てて実行する。
 * </p>
 *
 * <p>
 * 操作オブジェクトは永続化されず識別子も持たない、本サービスの中でだけ意味を持つモデルのためネスト型として置く。
 * </p>
 */
@ApplicationScoped
@AllArgsConstructor
public class ArticleAlbumAttachmentService implements DomainService {

    private final AlbumAccessService albumAccessService;

    /**
     * 記事へアルバムを紐付ける試み
     *
     * @param article
     *            紐付け対象の記事
     * @param album
     *            紐付け先のアルバム
     */
    @CrossAggregateOperation
    public record AlbumAttachment(AlbumArticle article, Album album) {

        /** 公開中の記事には非公開のアルバムを紐付けられない */
        private static final ErrorResult UNPUBLISHED_ALBUM_ERROR = new ErrorResult(
                "albumId",
                "Cannot attach an unpublished album to a published article",
                "ARTICLE_PUBLISHED_ALBUM_NOT_PUBLISHED");

        /**
         * 紐付けてよいかを評価します（例外を投げず、結果を {@link Result} で返す）。
         *
         * @return 紐付けてよければ自身の {@code Success}、規則を満たさなければ検証エラーの {@code Failure}
         */
        public Result<AlbumAttachment> asValidated() {
            return policy().verify(this, Function.identity());
        }

        /**
         * 規則を満たすときだけアルバムを紐付けます。
         *
         * @param currentDateTime
         *            現在日時
         * @return 紐付け後の記事
         */
        public AlbumArticle attach(BusinessDateTime currentDateTime) {
            return asValidated()
                    .map(validated -> validated.article().setAlbumId(validated.album().id(), currentDateTime))
                    .resolve(BusinessRuleViolationException::fromErrors);
        }

        private static Policy<AlbumAttachment> policy() {
            return Policy.of(
                    attachment -> unpublishedAlbum(attachment).isEmpty(),
                    UNPUBLISHED_ALBUM_ERROR);
        }

        private static Optional<Album> unpublishedAlbum(@Nullable AlbumAttachment attachment) {
            return Optional.ofNullable(attachment)
                    .filter(candidate -> candidate.article().isPublic())
                    .map(AlbumAttachment::album)
                    .filter(not(Album::isPublished));
        }
    }

    /**
     * 記事にアルバムを紐付けます。
     *
     * @param article
     *            紐付け対象の記事
     * @param albumId
     *            紐付け先のアルバムID
     * @param currentDateTime
     *            現在日時
     * @return 紐付け後の記事。未存在なら {@code EntityNotFoundException}、規則を満たさない場合は
     *         {@link BusinessRuleViolationException} で失敗する
     */
    public Uni<AlbumArticle> attachAlbum(
            AlbumArticle article,
            Album.Id albumId,
            BusinessDateTime currentDateTime) {
        return albumAccessService.findExistingAndClaimReference(albumId)
                .map(album -> new AlbumAttachment(article, album))
                .map(attachment -> attachment.attach(currentDateTime));
    }
}
