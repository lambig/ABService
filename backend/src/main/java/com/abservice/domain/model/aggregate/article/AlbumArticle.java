package com.abservice.domain.model.aggregate.article;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.CrossAggregateTransition;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.lib.ErrorResult;
import java.util.Objects;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * アルバム紹介記事
 *
 * <p>
 * Album 集約への参照を持てる唯一の記事種別です（片方向関連）。参照は「なし・有効・失効」の3状態を {@link AlbumReference}
 * が型で表します。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class AlbumArticle implements Article {
    /** 種別によらず共通する状態 */
    @EqualsAndHashCode.Include
    @NonNull
    private final ArticleCore core;
    /** アルバムへの参照（参照なし・有効な参照・失効した参照のいずれか） */
    @NonNull
    private final AlbumReference albumReference;

    /** core必須違反時のエラー */
    private static final ErrorResult CORE_REQUIRED_ERROR = new ErrorResult(
            "core",
            "Article core cannot be null",
            "ARTICLE_CORE_REQUIRED");

    @DomainConstructor
    private AlbumArticle(@NonNull ArticleCore core, @NonNull AlbumReference albumReference) {
        this.core = core;
        this.albumReference = albumReference;
    }

    @DomainFactory
    private static @NonNull AlbumArticle factory(@Nullable ArticleCore core,
            @Nullable AlbumReference albumReference) {
        return Policy.<Stub>of(
                self -> self.core() != null,
                CORE_REQUIRED_ERROR)
                .verify(
                        new Stub(
                                core,
                                albumReference),
                        Stub::asAlbumArticle)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(ArticleCore core, AlbumReference albumReference) {

        @AggregateFactory
        @NonNull
        AlbumArticle asAlbumArticle() {
            return new AlbumArticle(
                    Objects.requireNonNull(core),
                    Objects.requireNonNullElseGet(albumReference(), AlbumReference::none));
        }
    }

    /**
     * 記事がアルバム紹介記事であればその型で取り出します。
     *
     * <p>
     * アルバムへの参照を持てるのは本種別だけのため、参照に関わる判断は取り出せた場合にのみ成立します。取り出せない種別は
     * 「参照を持たない」ではなく「参照という概念を持たない」ことを表します。
     * </p>
     *
     * @param article
     *            記事
     * @return アルバム紹介記事であればその値、他の種別なら空
     */
    public static @NonNull Optional<AlbumArticle> from(@Nullable Article article) {
        return Optional.ofNullable(article)
                .filter(AlbumArticle.class::isInstance)
                .map(AlbumArticle.class::cast);
    }

    /**
     * 共通状態とアルバム参照からアルバム紹介記事を組み立てます。
     *
     * @param core
     *            共通状態
     * @param albumReference
     *            アルバム参照
     * @return アルバム紹介記事
     */
    static @NonNull AlbumArticle of(@NonNull ArticleCore core, @NonNull AlbumReference albumReference) {
        return AlbumArticle.factory(core, albumReference);
    }

    @Override
    public @NonNull AlbumArticle withCore(@NonNull ArticleCore newCore) {
        return AlbumArticle.factory(newCore, albumReference);
    }

    @Override
    public @NonNull ArticleType articleType() {
        return ArticleType.ALBUM;
    }

    /**
     * アルバムIDを設定
     *
     * @param newAlbumId
     *            新しいアルバムID
     * @param currentDateTime
     *            現在日時
     * @return 更新されたアルバム紹介記事
     */
    @CrossAggregateTransition
    public @NonNull AlbumArticle setAlbumId(Album.@NonNull Id newAlbumId,
            @NonNull BusinessDateTime currentDateTime) {
        return withAlbumReference(AlbumReference.of(newAlbumId), currentDateTime);
    }

    /**
     * 参照先アルバムの削除により、アルバム参照を失効させます。
     *
     * <p>
     * 有効な参照を持つ場合のみ失効状態へ遷移し、旧アルバムID・失効日時・理由を残します。参照を持たない場合や既に失効している場合は 現在の状態を保ちます。
     * </p>
     *
     * @param reason
     *            失効の理由
     * @param currentDateTime
     *            現在日時
     * @return 更新されたアルバム紹介記事
     */
    public @NonNull AlbumArticle loseAlbumReference(@NonNull AlbumReferenceLostReason reason,
            @NonNull BusinessDateTime currentDateTime) {
        return albumReference.activeAlbumId()
                .map(
                        activeId -> withAlbumReference(
                                new AlbumReference.Lost(
                                        activeId,
                                        currentDateTime,
                                        reason),
                                currentDateTime))
                .orElse(this);
    }

    private @NonNull AlbumArticle withAlbumReference(@NonNull AlbumReference newAlbumReference,
            @NonNull BusinessDateTime currentDateTime) {
        return AlbumArticle.factory(
                core.touch(currentDateTime),
                newAlbumReference);
    }
}
