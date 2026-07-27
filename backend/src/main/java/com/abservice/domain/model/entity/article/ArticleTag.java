package com.abservice.domain.model.entity.article;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 記事タグエンティティ
 *
 * <p>
 * 記事のカテゴライズ・フィルタリング用のタグを表現するエンティティです。
 * </p>
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ArticleTag implements DomainEntity<ArticleTag, ArticleTag.@NonNull Id> {
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    @NonNull
    private final String name;

    // 唯一の構築経路。Policy検証をここに一本化することで、createだけでなくreconstructも
    // 含めどの経路からも検証を迂回できない（#101）。
    private ArticleTag(@NonNull Id id, @NonNull String name) {
        this.id = id;
        this.name = requireName(name);
    }

    private static @NonNull String requireName(@Nullable String name) {
        return Policy.<String>of(
                StringUtils::isNotBlank,
                () -> new ErrorResult(
                        "name",
                        "Tag name cannot be blank",
                        "TAG_NAME_REQUIRED"))
                .verify(name, Function.identity())
                .resolve(Policy::illegalArgument);
    }

    /**
     * 新規タグを生成
     *
     * @param name
     *            タグ名
     * @return 新規ArticleTag
     */
    public static @NonNull ArticleTag create(@NonNull String name) {
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
            Policy.<String>all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "ArticleTag ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult(
                                    "value",
                                    "ArticleTag ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")))
                    .verify(value, Function.identity())
                    .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
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
