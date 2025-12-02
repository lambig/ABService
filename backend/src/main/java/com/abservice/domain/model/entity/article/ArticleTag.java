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
