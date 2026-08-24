package com.abservice.domain.model.aggregate.article;

import static java.util.function.Predicate.not;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.CrossAggregateTransition;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.article.AlbumReference;
import com.abservice.domain.model.vo.article.AlbumReferenceLostReason;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.MarkupContent;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * 記事集約ルート
 *
 * <p>
 * ブログ記事、アルバム紹介記事、お品書き掲載記事など、「公開情報」そのものを管理する集約です。 アルバム記事の場合は Album
 * 集約への参照を持ちます（片方向関連）。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Article implements Aggregate<Article, Article.@NonNull Id> {
    /** 記事ID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** 記事種別 */
    @NonNull
    private final ArticleType articleType;
    /** アルバムへの参照（参照なし・有効な参照・失効した参照のいずれか） */
    @NonNull
    private final AlbumReference albumReference;
    /** 記事タイトル */
    @NonNull
    private final ArticleTitle title;
    /** 記事本文 */
    @Nullable
    private final MarkupContent body;
    /** お品書き・一覧表示用の短い紹介文 */
    @Nullable
    private final String introShort;
    /** 公開日 */
    @Nullable
    private final BusinessDateTime publishedAt;
    /** 更新日（業務上の更新。監査カラムとは別概念） */
    @Nullable
    private final BusinessDateTime updatedAtBusiness;
    /** 公開/非公開フラグ */
    private final boolean publicFlag;
    /** 記事タグのリスト */
    @NonNull
    private final List<ArticleTag> tags;

    @DomainConstructor
    private Article(@NonNull Id id, @NonNull ArticleType articleType, @NonNull AlbumReference albumReference,
            @NonNull ArticleTitle title, @Nullable MarkupContent body, @Nullable String introShort,
            @Nullable BusinessDateTime publishedAt, @Nullable BusinessDateTime updatedAtBusiness,
            boolean publicFlag, @NonNull List<ArticleTag> tags) {
        this.id = id;
        this.articleType = articleType;
        this.albumReference = albumReference;
        this.title = title;
        this.body = body;
        this.introShort = introShort;
        this.publishedAt = publishedAt;
        this.updatedAtBusiness = updatedAtBusiness;
        this.publicFlag = publicFlag;
        this.tags = tags;
    }

    @DomainFactory
    private static @NonNull Article factory(@Nullable Id id, @Nullable ArticleType articleType,
            @Nullable AlbumReference albumReference, @Nullable ArticleTitle title, @Nullable MarkupContent body,
            @Nullable String introShort, @Nullable BusinessDateTime publishedAt,
            @Nullable BusinessDateTime updatedAtBusiness, boolean publicFlag, @Nullable List<ArticleTag> tags) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.articleType() != null,
                        TYPE_REQUIRED_ERROR),
                Policy.of(
                        self -> self.title() != null,
                        TITLE_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                articleType,
                                albumReference,
                                title,
                                body,
                                introShort,
                                publishedAt,
                                updatedAtBusiness,
                                publicFlag,
                                tags),
                        Stub::asArticle)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, ArticleType articleType, AlbumReference albumReference, ArticleTitle title,
            MarkupContent body, String introShort, BusinessDateTime publishedAt,
            BusinessDateTime updatedAtBusiness, boolean publicFlag, List<ArticleTag> tags) {

        @AggregateFactory
        @NonNull
        Article asArticle() {
            return new Article(Objects.requireNonNull(id), Objects.requireNonNull(articleType),
                    Objects.requireNonNullElseGet(albumReference(), AlbumReference::none),
                    Objects.requireNonNull(title), body(), introShort(), publishedAt(), updatedAtBusiness(),
                    publicFlag(), Objects.requireNonNull(tags));
        }
    }

    /**
     * 新規記事を生成
     *
     * @param articleType
     *            記事種別
     * @param albumId
     *            アルバムID（nullable）
     * @param title
     *            タイトル
     * @param body
     *            本文（nullable）
     * @param introShort
     *            ショート紹介文（nullable）
     * @return 新規Article
     */
    public static @NonNull Article create(@NonNull ArticleType articleType, Album.@Nullable Id albumId,
            @NonNull ArticleTitle title, @Nullable MarkupContent body, @Nullable String introShort) {
        return Article.factory(
                Id.generate(),
                articleType,
                AlbumReference.of(albumId),
                title,
                body,
                introShort,
                null,
                null,
                false,
                Collections.emptyList());
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            記事ID
     * @param articleType
     *            記事種別
     * @param albumReference
     *            アルバム参照（参照なし・有効な参照・失効した参照のいずれか）
     * @param title
     *            タイトル
     * @param body
     *            本文（nullable）
     * @param introShort
     *            ショート紹介文（nullable）
     * @param publishedAt
     *            公開日（nullable）
     * @param updatedAtBusiness
     *            更新日（nullable）
     * @param publicFlag
     *            公開フラグ
     * @param tags
     *            タグリスト
     * @return 再構成されたArticle
     */
    @DomainFactory
    public static @NonNull Article reconstruct(@NonNull Id id, @NonNull ArticleType articleType,
            @NonNull AlbumReference albumReference, @NonNull ArticleTitle title, @Nullable MarkupContent body,
            @Nullable String introShort, @Nullable BusinessDateTime publishedAt,
            @Nullable BusinessDateTime updatedAtBusiness, boolean publicFlag, @NonNull List<ArticleTag> tags) {
        return Article.factory(
                id,
                articleType,
                albumReference,
                title,
                body,
                introShort,
                publishedAt,
                updatedAtBusiness,
                publicFlag,
                tags);
    }

    /**
     * 記事タイトルを変更
     *
     * @param newTitle
     *            新しい記事タイトル
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article changeTitle(@NonNull ArticleTitle newTitle, @NonNull BusinessDateTime currentDateTime) {
        return Article.factory(
                id,
                articleType,
                albumReference,
                newTitle,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    /**
     * 記事本文を変更
     *
     * @param newBody
     *            新しい記事本文
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article changeBody(@Nullable MarkupContent newBody, @NonNull BusinessDateTime currentDateTime) {
        return Article.factory(
                id,
                articleType,
                albumReference,
                title,
                newBody,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    /**
     * ショート紹介文を変更
     *
     * @param newIntroShort
     *            新しいショート紹介文
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article changeIntroShort(@Nullable String newIntroShort,
            @NonNull BusinessDateTime currentDateTime) {
        return Article.factory(
                id,
                articleType,
                albumReference,
                title,
                body,
                newIntroShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    /**
     * 記事を公開
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    @CrossAggregateTransition
    public @NonNull Article publish(@NonNull BusinessDateTime currentDateTime) {
        return Article.factory(
                id,
                articleType,
                albumReference,
                title,
                body,
                introShort,
                resolvePublishedAt(currentDateTime),
                currentDateTime,
                true,
                tags);
    }

    private @NonNull BusinessDateTime resolvePublishedAt(@NonNull BusinessDateTime currentDateTime) {
        return Optional.ofNullable(publishedAt)
                .orElse(currentDateTime);
    }

    /**
     * 記事を非公開化
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article unpublish(@NonNull BusinessDateTime currentDateTime) {
        return Article.factory(
                id,
                articleType,
                albumReference,
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                false,
                tags);
    }

    /**
     * 公開されているかどうか
     *
     * @return 公開フラグ
     */
    public boolean isPublic() {
        return publicFlag;
    }

    /**
     * アルバムIDを設定（アルバム記事に変換）
     *
     * @param newAlbumId
     *            新しいアルバムID
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    @CrossAggregateTransition
    public @NonNull Article setAlbumId(Album.@NonNull Id newAlbumId, @NonNull BusinessDateTime currentDateTime) {
        Policy.<Article>of(
                a -> a.articleType() == ArticleType.ALBUM,
                () -> new ErrorResult("articleType", "Cannot set album ID for non-ALBUM article type",
                        "ARTICLE_TYPE_NOT_ALBUM"))
                .verify(this, Function.identity())
                .resolve(errors -> new IllegalStateException(errors.getFirst().message()));
        return Article.factory(
                id,
                articleType,
                AlbumReference.of(newAlbumId),
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    /**
     * 記事種別を変更
     *
     * @param newArticleType
     *            新しい記事種別
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article changeArticleType(@NonNull ArticleType newArticleType,
            @NonNull BusinessDateTime currentDateTime) {
        return Article.factory(
                id,
                newArticleType,
                resolveReferenceFor(newArticleType),
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    /**
     * 参照先アルバムの削除により、アルバム参照を失効させます。
     *
     * <p>
     * 有効な参照を持つ場合のみ失効状態へ遷移し、旧アルバムID・失効日時・理由を残します。参照を持たない場合や既に失効している場合は
     * 現在の状態を保ちます。記事種別は変更しません。
     * </p>
     *
     * @param reason
     *            失効の理由
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article loseAlbumReference(@NonNull AlbumReferenceLostReason reason,
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

    private @NonNull Article withAlbumReference(@NonNull AlbumReference newAlbumReference,
            @NonNull BusinessDateTime currentDateTime) {
        return Article.factory(
                id,
                articleType,
                newAlbumReference,
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    /*
     * NULL-TOLERANT: 記事種別のnull検証はfactoryのPolicyが担うため、ここでnullを弾くと検証エラーが
     * NullPointerExceptionに化ける。参照を落とす側（OTHER相当）へ寄せてfactoryへ渡す。
     */
    private @NonNull AlbumReference resolveReferenceFor(@Nullable ArticleType newArticleType) {
        return switch (orReferenceDropping(newArticleType)) {
            case ALBUM -> albumReference;
            case NOTE, NEWS, EVENT, OTHER -> AlbumReference.none();
        };
    }

    private static @NonNull ArticleType orReferenceDropping(@Nullable ArticleType articleType) {
        return Optional.ofNullable(articleType)
                .orElse(ArticleType.OTHER);
    }

    /**
     * タグを追加
     *
     * @param tag
     *            追加するタグ
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article addTag(@NonNull ArticleTag tag, @NonNull BusinessDateTime currentDateTime) {
        final var validatedTag = Policy.<ArticleTag>of(
                Objects::nonNull,
                TAG_REQUIRED_ERROR)
                .verify(tag, Function.identity()).resolve(Policy::illegalArgument);
        // DYNAMIC-MESSAGE: メッセージにIDを埋め込むため、静的ErrorResultではなく都度生成のSupplierを使う
        Policy.<ArticleTag>of(
                t -> tags.stream().noneMatch(t::equivalentTo),
                () -> new ErrorResult(
                        "tag",
                        "Tag with ID " + validatedTag.id().value() + " already exists",
                        "ARTICLE_TAG_DUPLICATE"))
                .verify(validatedTag, Function.identity())
                .resolve(BusinessRuleViolationException::fromErrors);
        return Article.factory(
                id,
                articleType,
                albumReference,
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                Stream.concat(tags.stream(), Stream.of(validatedTag)).toList());
    }

    /**
     * タグを削除
     *
     * @param tagId
     *            削除するタグのID
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article removeTag(ArticleTag.@NonNull Id tagId, @NonNull BusinessDateTime currentDateTime) {
        final var validatedTagId = Policy.<ArticleTag.Id>of(
                Objects::nonNull,
                TAG_ID_REQUIRED_ERROR)
                .verify(tagId, Function.identity()).resolve(Policy::illegalArgument);
        return Article.factory(
                id,
                articleType,
                albumReference,
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags.stream().filter(not(t -> t.hasId(validatedTagId))).toList());
    }

    /**
     * すべてのタグを取得（不変リスト）
     *
     * @return タグのリスト
     */
    public @NonNull List<ArticleTag> getTags() {
        return Collections.unmodifiableList(tags);
    }

    /** articleType必須違反時のエラー */
    private static final ErrorResult TYPE_REQUIRED_ERROR = new ErrorResult(
            "articleType",
            "Article type cannot be null",
            "ARTICLE_TYPE_REQUIRED");

    /** title必須違反時のエラー */
    private static final ErrorResult TITLE_REQUIRED_ERROR = new ErrorResult(
            "title",
            "Article title cannot be null",
            "ARTICLE_TITLE_REQUIRED");

    /** tag必須違反時のエラー */
    private static final ErrorResult TAG_REQUIRED_ERROR = new ErrorResult(
            "tag",
            "Tag cannot be null",
            "ARTICLE_TAG_REQUIRED");

    /** tagId必須違反時のエラー */
    private static final ErrorResult TAG_ID_REQUIRED_ERROR = new ErrorResult(
            "tagId",
            "Tag ID cannot be null",
            "ARTICLE_TAG_ID_REQUIRED");

    /**
     * 記事ID
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<Article> {
        /** value空白違反時のエラー */
        private static final ErrorResult ID_BLANK_ERROR = new ErrorResult(
                "value",
                "Article ID cannot be blank",
                "ID_BLANK");

        public Id {
            idPolicy(value)
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        private static Policy<String> idPolicy(@Nullable String value) {
            return Policy.all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            ID_BLANK_ERROR),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult(
                                    "value",
                                    "Article ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")));
        }

        /**
         * UUIDv7を生成してArticle.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArticle.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }

        /**
         * 外部入力（文字列）からArticle.Idを生成します。
         *
         * <p>
         * 例外をスローせず、検証結果を {@link com.abservice.lib.Result} で返します。 信頼できる内部生成には
         * {@link #of(String)} を使用してください。
         * </p>
         *
         * @param value
         *            ID値を表す文字列
         * @return 成功時は {@code Id}、失敗時はエラー
         */
        public static Result<Id> fromInput(@Nullable String value) {
            return idPolicy(value)
                    .verify(value, Id::new);
        }
    }

    @Override
    public @NonNull Id id() {
        return id;
    }
}
