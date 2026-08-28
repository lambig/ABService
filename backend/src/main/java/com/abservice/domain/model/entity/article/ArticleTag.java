package com.abservice.domain.model.entity.article;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
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
    /** タグID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** タグ名 */
    @NonNull
    private final String name;

    /** タグ名の最大長（{@code article_tag.name} カラムの上限に一致） */
    private static final int MAX_NAME_LENGTH = 100;

    private ArticleTag(@NonNull Id id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    private static @NonNull ArticleTag factory(@Nullable Id id, @Nullable String name) {
        return namePolicy()
                .verify(name, validName -> new Stub(id, validName).asArticleTag())
                .resolve(Policy::illegalArgument);
    }

    private static Policy<String> namePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "name",
                                "Tag name cannot be blank",
                                "TAG_NAME_REQUIRED")),
                Policy.of(
                        (String value) -> StringUtils.length(value) <= MAX_NAME_LENGTH,
                        () -> new ErrorResult(
                                "name",
                                "タグ名は" + MAX_NAME_LENGTH + "文字以内です",
                                "TAG_NAME_TOO_LONG")));
    }

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
     * 外部入力（文字列）から新規タグを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #create(String)} を使用してください。
     * </p>
     *
     * @param name
     *            タグ名を表す文字列
     * @return 成功時は {@code ArticleTag}、失敗時はエラー
     */
    public static Result<ArticleTag> fromInput(@Nullable String name) {
        return namePolicy().verify(name, ArticleTag::create);
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
            idPolicy(value)
                    .verify(value, Function.identity())
                    .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        }

        private static Policy<String> idPolicy(@Nullable String value) {
            return Policy.all(
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
                                    "ID_INVALID_UUID")));
        }

        /**
         * UUIDv7を生成してArticleTag.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からArticleTag.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }

        /**
         * 外部入力（文字列）からArticleTag.Idを生成します。
         *
         * <p>
         * 例外をスローせず、検証結果を {@link Result} で返します。信頼できる内部生成には {@link #of(String)}
         * を使用してください。
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
