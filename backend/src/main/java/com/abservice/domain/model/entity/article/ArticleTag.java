package com.abservice.domain.model.entity.article;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import java.util.Objects;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
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

    // 全フィールドを受け取る唯一の構築経路。自身では検証しないため、factory以外から呼ばせない
    // （ArchUnitで強制、#101）。
    private ArticleTag(@NonNull Id id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    // 生の全項目を受け取り、Policy検証を経てArticleTagを生成する唯一のfactory（#101）。
    private static @NonNull ArticleTag factory(@Nullable Id id, @Nullable String name) {
        return Policy.<Stub>of(
                self -> StringUtils.isNotBlank(self.name()),
                () -> new ErrorResult(
                        "name",
                        "Tag name cannot be blank",
                        "TAG_NAME_REQUIRED"))
                .verify(new Stub(id, name), Stub::asArticleTag)
                .resolve(Policy::illegalArgument);
    }

    // ArticleTagのAllArgsConstructorと同形の、制約を持たないdumbな入れ物。全フィールドが自明にnullable
    // なので@NullUnmarkedでNullAwareの対象外にし、個別の@Nullable注釈を省く。
    // ArchUnit（stubShouldMatchEnclosingConstructor）が実コンストラクタとの引数一致を機械的に強制する。
    @NullUnmarked
    private record Stub(Id id, String name) {

        @AggregateFactory
        @NonNull
        ArticleTag asArticleTag() {
            return new ArticleTag(Objects.requireNonNull(id), Objects.requireNonNull(name));
        }
    }

    /**
     * 新規タグを生成
     *
     * @param name
     *            タグ名
     * @return 新規ArticleTag
     */
    public static @NonNull ArticleTag create(@NonNull String name) {
        return ArticleTag.factory(Id.generate(), name);
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
        return ArticleTag.factory(id, name);
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
