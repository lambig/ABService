package com.abservice.domain.model.entity.article;

import static java.util.function.Predicate.not;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

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
public class ArticleTag implements DomainEntity<ArticleTag, ArticleTag.@NonNull Id> {
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    @NonNull
    private final String name;

    /**
     * 新規タグを生成
     *
     * @param name
     *            タグ名
     * @return 新規ArticleTag
     */
    public static @NonNull ArticleTag create(@NonNull String name) {
        var validatedName = Optional.ofNullable(name).filter(not(String::isBlank))
                .orElseThrow(() -> new IllegalArgumentException("Tag name cannot be blank"));
        return new ArticleTag(Id.generate(), validatedName);
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
    public static @NonNull ArticleTag reconstruct(@NonNull Id id, @NonNull String name) {
        return new ArticleTag(id, name);
    }

    /**
     * 記事タグID
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<ArticleTag> {
        public Id {
            Optional.ofNullable(value).filter(not(String::isBlank))
                    .orElseThrow(() -> new IllegalArgumentException("ArticleTag ID cannot be blank"));
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("ArticleTag ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してArticleTag.Idを作成
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArticleTag.Idを生成
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
