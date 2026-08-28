package com.abservice.domain.model.aggregate.article;

import static java.util.function.Predicate.not;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.article.ArticleTitle;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.lib.ErrorResult;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * 記事種別によらず共通する記事の状態
 *
 * <p>
 * {@link Article} の各種別が等しく持つ項目と、それらに対する変更操作をここへ集約します。種別に固有の項目（アルバム参照など）は
 * 各種別のクラスが持ち、本クラスへは入れません。種別ごとのクラスが共通項目を再宣言せずに済むため、種別を増やしても
 * 共通項目の定義と変更操作は1箇所に留まります。
 * </p>
 *
 * <p>
 * 変更操作は新しい {@code ArticleCore} を返すのみで、どの種別のクラスへ載せ直すかは呼び出し側（{@link Article} の
 * 既定実装）が決めます。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ArticleCore {
    /** 記事ID */
    @EqualsAndHashCode.Include
    private final Article.@NonNull Id id;
    /** 記事タイトル */
    @NonNull
    private final ArticleTitle title;
    /**
     * 記事本文（Null Objectパターン。本文なしは {@code MarkupContent.EMPTY}）
     *
     * <p>
     * 本文は「無い」ことがあり得ない項目のため、空であることは認めるが null は持たない。値が真に無いことがある項目
     * （{@code publishedAt} / {@code updatedAtBusiness}）とは扱いを分ける。
     * </p>
     */
    @NonNull
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

    @DomainConstructor
    private ArticleCore(Article.@NonNull Id id, @NonNull ArticleTitle title, @NonNull MarkupContent body,
            @Nullable String introShort, @Nullable BusinessDateTime publishedAt,
            @Nullable BusinessDateTime updatedAtBusiness, boolean publicFlag, @NonNull List<ArticleTag> tags) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.introShort = introShort;
        this.publishedAt = publishedAt;
        this.updatedAtBusiness = updatedAtBusiness;
        this.publicFlag = publicFlag;
        this.tags = tags;
    }

    @DomainFactory
    static @NonNull ArticleCore factory(Article.@Nullable Id id, @Nullable ArticleTitle title,
            @Nullable MarkupContent body, @Nullable String introShort, @Nullable BusinessDateTime publishedAt,
            @Nullable BusinessDateTime updatedAtBusiness, boolean publicFlag, @Nullable List<ArticleTag> tags) {
        return Policy.<Stub>of(
                self -> self.title() != null,
                TITLE_REQUIRED_ERROR)
                .verify(
                        new Stub(
                                id,
                                title,
                                body,
                                introShort,
                                publishedAt,
                                updatedAtBusiness,
                                publicFlag,
                                tags),
                        Stub::asArticleCore)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Article.Id id, ArticleTitle title, MarkupContent body, String introShort,
            BusinessDateTime publishedAt, BusinessDateTime updatedAtBusiness, boolean publicFlag,
            List<ArticleTag> tags) {

        @AggregateFactory
        @NonNull
        ArticleCore asArticleCore() {
            return new ArticleCore(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(title),
                    Objects.requireNonNullElse(body(), MarkupContent.EMPTY),
                    introShort(),
                    publishedAt(),
                    updatedAtBusiness(),
                    publicFlag(),
                    Objects.requireNonNull(tags));
        }
    }

    /**
     * 新規記事の共通状態を生成します。
     *
     * <p>
     * 業務上の更新日時には作成日時を入れる。記事を書き起こすことも業務上の更新の一つであり、null のままにすると
     * 作成しただけの記事が作業順の並びで末尾に固まって見つけられなくなる。
     * </p>
     *
     * @param title
     *            タイトル
     * @param body
     *            本文（nullable）
     * @param introShort
     *            ショート紹介文（nullable）
     * @param currentDateTime
     *            現在日時
     * @return 未公開・タグなしの共通状態
     */
    static @NonNull ArticleCore create(@NonNull ArticleTitle title, @Nullable MarkupContent body,
            @Nullable String introShort, @NonNull BusinessDateTime currentDateTime) {
        return ArticleCore.factory(
                Article.Id.generate(),
                title,
                body,
                introShort,
                null,
                currentDateTime,
                false,
                Collections.emptyList());
    }

    /**
     * 永続化層からの再構成に用いる共通状態を生成します。
     *
     * @param id
     *            記事ID
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
     * @return 再構成された共通状態
     */
    @DomainFactory
    static @NonNull ArticleCore reconstruct(Article.@NonNull Id id, @NonNull ArticleTitle title,
            @Nullable MarkupContent body, @Nullable String introShort, @Nullable BusinessDateTime publishedAt,
            @Nullable BusinessDateTime updatedAtBusiness, boolean publicFlag, @NonNull List<ArticleTag> tags) {
        return ArticleCore.factory(
                id,
                title,
                body,
                introShort,
                publishedAt,
                updatedAtBusiness,
                publicFlag,
                tags);
    }

    @NonNull
    ArticleCore changeTitle(@NonNull ArticleTitle newTitle, @NonNull BusinessDateTime currentDateTime) {
        return ArticleCore.factory(
                id,
                newTitle,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    @NonNull
    ArticleCore changeBody(@Nullable MarkupContent newBody, @NonNull BusinessDateTime currentDateTime) {
        return ArticleCore.factory(
                id,
                title,
                newBody,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    @NonNull
    ArticleCore changeIntroShort(@Nullable String newIntroShort, @NonNull BusinessDateTime currentDateTime) {
        return ArticleCore.factory(
                id,
                title,
                body,
                newIntroShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    @NonNull
    ArticleCore publish(@NonNull BusinessDateTime currentDateTime) {
        return ArticleCore.factory(
                id,
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

    @NonNull
    ArticleCore unpublish(@NonNull BusinessDateTime currentDateTime) {
        return ArticleCore.factory(
                id,
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                false,
                tags);
    }

    /**
     * 業務上の更新日時だけを進めた共通状態を返します。
     *
     * <p>
     * 種別に固有の項目（アルバム参照など）が変わったときに、共通項目の更新日時を揃えるために使います。
     * </p>
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新日時を進めた共通状態
     */
    @NonNull
    ArticleCore touch(@NonNull BusinessDateTime currentDateTime) {
        return ArticleCore.factory(
                id,
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                tags);
    }

    @NonNull
    ArticleCore addTag(@NonNull ArticleTag tag, @NonNull BusinessDateTime currentDateTime) {
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
        return ArticleCore.factory(
                id,
                title,
                body,
                introShort,
                publishedAt,
                currentDateTime,
                publicFlag,
                Stream.concat(tags.stream(), Stream.of(validatedTag)).toList());
    }

    @NonNull
    ArticleCore removeTag(ArticleTag.@NonNull Id tagId, @NonNull BusinessDateTime currentDateTime) {
        final var validatedTagId = Policy.<ArticleTag.Id>of(
                Objects::nonNull,
                TAG_ID_REQUIRED_ERROR)
                .verify(tagId, Function.identity()).resolve(Policy::illegalArgument);
        return ArticleCore.factory(
                id,
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
    @NonNull
    List<ArticleTag> getTags() {
        return Collections.unmodifiableList(tags);
    }
}
