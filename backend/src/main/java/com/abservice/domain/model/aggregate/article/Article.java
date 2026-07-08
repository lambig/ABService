package com.abservice.domain.model.aggregate.article;

import static java.util.function.Predicate.not;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleType;
import com.abservice.domain.model.vo.article.MarkupContent;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 記事集約ルート
 *
 * <p>
 * ブログ記事、アルバム紹介記事、お品書き掲載記事など、「公開情報」そのものを管理する集約です。 アルバム記事の場合は Album
 * 集約への参照を持ちます（片方向関連）。
 * </p>
 */
@With(AccessLevel.PRIVATE)
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Article implements Aggregate<Article, Article.@NonNull Id> {
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    @NonNull
    private final ArticleType articleType;
    private final Album.@Nullable Id albumId; // nullable: アルバム記事の場合のみ参照
    @NonNull
    private final String title;
    @Nullable
    private final MarkupContent body; // nullable: 記事本文（マークアップ可能）
    @Nullable
    private final String introShort; // nullable: お品書きや一覧表示用概要
    @Nullable
    private final BusinessDateTime publishedAt; // nullable: 公開日（掲載日の業務意味）
    @Nullable
    private final BusinessDateTime updatedAtBusiness; // nullable: 更新日（「修正した」意味。監査とは別概念）
    private final boolean publicFlag; // 公開/非公開フラグ
    @NonNull
    private final List<ArticleTag> tags; // 記事タグのリスト

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
            @NonNull String title, @Nullable MarkupContent body, @Nullable String introShort) {
        final var validatedType = Optional.ofNullable(articleType)
                .orElseThrow(() -> new IllegalArgumentException("Article type cannot be null"));
        final var validatedTitle = Optional.ofNullable(title).filter(not(String::isBlank))
                .orElseThrow(() -> new IllegalArgumentException("Article title cannot be null or blank"));
        return new Article(Id.generate(), validatedType, albumId, validatedTitle, body, introShort, null, null, false,
                Collections.emptyList());
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            記事ID
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
    @SuppressWarnings("checkstyle:ParameterNumber") // 永続化からの再構成で全項目を受け取るため引数が多い
    public static @NonNull Article reconstruct(@NonNull Id id, @NonNull ArticleType articleType,
            Album.@Nullable Id albumId, @NonNull String title, @Nullable MarkupContent body,
            @Nullable String introShort, @Nullable BusinessDateTime publishedAt,
            @Nullable BusinessDateTime updatedAtBusiness, boolean publicFlag, @NonNull List<ArticleTag> tags) {
        return new Article(id, articleType, albumId, title, body, introShort, publishedAt, updatedAtBusiness,
                publicFlag, tags);
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
    public @NonNull Article changeTitle(@NonNull String newTitle, @NonNull BusinessDateTime currentDateTime) {
        final var validatedTitle = Optional.ofNullable(newTitle).filter(not(String::isBlank))
                .orElseThrow(() -> new IllegalArgumentException("Article title cannot be null or blank"));
        return withTitle(validatedTitle).withUpdatedAtBusiness(currentDateTime);
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
        return withBody(newBody).withUpdatedAtBusiness(currentDateTime);
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
        return withIntroShort(newIntroShort).withUpdatedAtBusiness(currentDateTime);
    }

    /**
     * 記事を公開
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article publish(@NonNull BusinessDateTime currentDateTime) {
        return withPublicFlag(true).withPublishedAt(publishedAt == null ? currentDateTime : publishedAt)
                .withUpdatedAtBusiness(currentDateTime);
    }

    /**
     * 記事を非公開化
     *
     * @param currentDateTime
     *            現在日時
     * @return 更新されたArticle
     */
    public @NonNull Article unpublish(@NonNull BusinessDateTime currentDateTime) {
        return withPublicFlag(false).withUpdatedAtBusiness(currentDateTime);
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
    public @NonNull Article setAlbumId(Album.@NonNull Id newAlbumId, @NonNull BusinessDateTime currentDateTime) {
        if (articleType != ArticleType.ALBUM) {
            throw new IllegalStateException("Cannot set album ID for non-ALBUM article type");
        }
        return withAlbumId(newAlbumId).withUpdatedAtBusiness(currentDateTime);
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
        final var validatedType = Optional.ofNullable(newArticleType)
                .orElseThrow(() -> new IllegalArgumentException("Article type cannot be null"));
        // ALBUM以外の種別に変更する場合、albumIdをクリア
        if (validatedType != ArticleType.ALBUM && albumId != null) {
            return withArticleType(validatedType).withAlbumId(null).withUpdatedAtBusiness(currentDateTime);
        }
        return withArticleType(validatedType).withUpdatedAtBusiness(currentDateTime);
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
        final var validatedTag = Optional.ofNullable(tag)
                .orElseThrow(() -> new IllegalArgumentException("Tag cannot be null"));
        // IDの重複チェック
        if (tags.stream().anyMatch(t -> t.id().equals(validatedTag.id()))) {
            throw new IllegalArgumentException("Tag with ID " + validatedTag.id().value() + " already exists");
        }
        return withTags(Stream.concat(tags.stream(), Stream.of(validatedTag)).toList())
                .withUpdatedAtBusiness(currentDateTime);
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
        final var validatedTagId = Optional.ofNullable(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag ID cannot be null"));
        final var newTags = tags.stream().filter(t -> !t.id().equals(validatedTagId))
                .collect(java.util.stream.Collectors.toList());
        return withTags(Collections.unmodifiableList(newTags)).withUpdatedAtBusiness(currentDateTime);
    }

    /**
     * すべてのタグを取得（不変リスト）
     *
     * @return タグのリスト
     */
    public @NonNull List<ArticleTag> getTags() {
        return Collections.unmodifiableList(tags);
    }

    /**
     * 記事ID
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<Article> {
        public Id {
            Optional.ofNullable(value).filter(not(String::isBlank))
                    .orElseThrow(() -> new IllegalArgumentException("Article ID cannot be blank"));
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Article ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してArticle.Idを作成
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArticle.Idを生成
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }
    }

    @Override
    public @NonNull Id id() {
        return id;
    }
}
