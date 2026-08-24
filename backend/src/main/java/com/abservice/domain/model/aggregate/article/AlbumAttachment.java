package com.abservice.domain.model.aggregate.article;

import static java.util.function.Predicate.not;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.CrossAggregateOperation;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * 記事へアルバムを紐付ける試み
 *
 * <p>
 * 公開中の記事が非公開アルバムを参照する不整合を、紐付けの経路からも作らせない。参照先を伴って構築され、規則を満たすときだけ
 * 紐付けへ遷移させる。参照先を引く責務はドメインサービスが持ち、判定自体はI/Oを伴わない純粋な評価になる。
 * </p>
 *
 * @param article
 *            紐付け対象の記事
 * @param album
 *            紐付け先のアルバム
 */
@CrossAggregateOperation
public record AlbumAttachment(Article article, Album album) {

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
     * @throws BusinessRuleViolationException
     *             規則を満たさない場合
     */
    public Article attach(BusinessDateTime currentDateTime) {
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
