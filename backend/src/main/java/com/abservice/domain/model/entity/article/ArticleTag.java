package com.abservice.domain.model.entity.article;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 記事タグエンティティ
 *
 * <p>
 * 記事のカテゴライズ・フィルタリング用のタグを表現するエンティティです。
 * </p>
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ArticleTag implements DomainEntity<ArticleTag, ArticleTag.Id> {
    @EqualsAndHashCode.Include
    private final Id id;
    private final String name;

    /**
     * 新規タグを生成
     *
     * @param name
     *            タグ名
     * @return 新規ArticleTag
     */
    public static ArticleTag create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tag name cannot be blank");
        }
        return new ArticleTag(Id.generate(), name);
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            タグID
     * @param name
     *            タグ名
     * @return 再構成されたArticleTag
     */
    public static ArticleTag reconstruct(Id id, String name) {
        return new ArticleTag(id, name);
    }

    /**
     * 記事タグID
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(String value) implements EntityId<ArticleTag> {
        public Id {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("ArticleTag ID cannot be blank");
            }
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("ArticleTag ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してArticleTag.Idを作成
         */
        public static Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArticleTag.Idを生成
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
