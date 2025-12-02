package com.abservice.domain.model.aggregate.article;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.aggregate.album.Album;
import com.abservice.domain.model.entity.article.ArticleTag;
import com.abservice.domain.model.vo.article.ArticleType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;

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
public class Article implements Aggregate<Article, Article.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final ArticleType articleType;
    private final Album.Id albumId; // nullable: アルバム記事の場合のみ参照
    private final String title;
    private final String body;
    private final String introShort; // nullable: お品書きや一覧表示用概要
    private final LocalDateTime publishedAt; // nullable: 公開日（掲載日の業務意味）
    private final LocalDateTime updatedAtBusiness; // nullable: 更新日（「修正した」意味。監査とは別概念）
    private final boolean publicFlag; // 公開/非公開フラグ
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
     *            本文
     * @param introShort
     *            ショート紹介文（nullable）
     * @return 新規Article
     */
    public static Article create(ArticleType articleType, Album.Id albumId, String title, String body,
            String introShort) {
        if (articleType == null) {
            throw new IllegalArgumentException("Article type cannot be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Article title cannot be null or blank");
        }
        return new Article(Id.generate(), articleType, albumId, title, body, introShort, null, null, false,
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
     *            本文
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
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Article reconstruct(Id id, ArticleType articleType, Album.Id albumId, String title, String body,
            String introShort, LocalDateTime publishedAt, LocalDateTime updatedAtBusiness, boolean publicFlag,
            List<ArticleTag> tags) {
        return new Article(id, articleType, albumId, title, body, introShort, publishedAt, updatedAtBusiness,
                publicFlag, tags);
    }

    /**
     * 記事タイトルを変更
     *
     * @param newTitle
     *            新しい記事タイトル
     * @return 更新されたArticle
     */
    public Article changeTitle(String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("Article title cannot be null or blank");
        }
        return withTitle(newTitle).withUpdatedAtBusiness(LocalDateTime.now());
    }

    /**
     * 記事本文を変更
     *
     * @param newBody
     *            新しい記事本文
     * @return 更新されたArticle
     */
    public Article changeBody(String newBody) {
        return withBody(newBody).withUpdatedAtBusiness(LocalDateTime.now());
    }

    /**
     * ショート紹介文を変更
     *
     * @param newIntroShort
     *            新しいショート紹介文
     * @return 更新されたArticle
     */
    public Article changeIntroShort(String newIntroShort) {
        return withIntroShort(newIntroShort).withUpdatedAtBusiness(LocalDateTime.now());
    }

    /**
     * 記事を公開
     *
     * @return 更新されたArticle
     */
    public Article publish() {
        LocalDateTime now = LocalDateTime.now();
        return withPublicFlag(true).withPublishedAt(publishedAt == null ? now : publishedAt).withUpdatedAtBusiness(now);
    }

    /**
     * 記事を非公開化
     *
     * @return 更新されたArticle
     */
    public Article unpublish() {
        return withPublicFlag(false).withUpdatedAtBusiness(LocalDateTime.now());
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
     * @return 更新されたArticle
     */
    public Article setAlbumId(Album.Id newAlbumId) {
        if (articleType != ArticleType.ALBUM) {
            throw new IllegalStateException("Cannot set album ID for non-ALBUM article type");
        }
        return withAlbumId(newAlbumId).withUpdatedAtBusiness(LocalDateTime.now());
    }

    /**
     * 記事種別を変更
     *
     * @param newArticleType
     *            新しい記事種別
     * @return 更新されたArticle
     */
    public Article changeArticleType(ArticleType newArticleType) {
        if (newArticleType == null) {
            throw new IllegalArgumentException("Article type cannot be null");
        }
        // ALBUM以外の種別に変更する場合、albumIdをクリア
        if (newArticleType != ArticleType.ALBUM && albumId != null) {
            return withArticleType(newArticleType).withAlbumId(null).withUpdatedAtBusiness(LocalDateTime.now());
        }
        return withArticleType(newArticleType).withUpdatedAtBusiness(LocalDateTime.now());
    }

    /**
     * タグを追加
     *
     * @param tag
     *            追加するタグ
     * @return 更新されたArticle
     */
    public Article addTag(ArticleTag tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Tag cannot be null");
        }
        // IDの重複チェック
        if (tags.stream().anyMatch(t -> t.id().equals(tag.id()))) {
            throw new IllegalArgumentException("Tag with ID " + tag.id().value() + " already exists");
        }
        var newTags = new ArrayList<>(tags);
        newTags.add(tag);
        return withTags(Collections.unmodifiableList(newTags)).withUpdatedAtBusiness(LocalDateTime.now());
    }

    /**
     * タグを削除
     *
     * @param tagId
     *            削除するタグのID
     * @return 更新されたArticle
     */
    public Article removeTag(ArticleTag.Id tagId) {
        if (tagId == null) {
            throw new IllegalArgumentException("Tag ID cannot be null");
        }
        var newTags = tags.stream().filter(t -> !t.id().equals(tagId)).collect(java.util.stream.Collectors.toList());
        return withTags(Collections.unmodifiableList(newTags)).withUpdatedAtBusiness(LocalDateTime.now());
    }

    /**
     * すべてのタグを取得（不変リスト）
     *
     * @return タグのリスト
     */
    public List<ArticleTag> getTags() {
        return Collections.unmodifiableList(tags);
    }

    /**
     * 記事ID
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(String value) implements EntityId<Article> {
        public Id {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Article ID cannot be blank");
            }
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Article ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してArticle.Idを作成
         */
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArticle.Idを生成
         */
        public static Id of(String value) {
            return new Id(value);
        }
    }

    @Override
    public Id id() {
        return id;
    }
}
