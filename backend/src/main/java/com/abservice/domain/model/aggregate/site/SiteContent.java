package com.abservice.domain.model.aggregate.site;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.MarkupContent;
import com.abservice.domain.model.vo.site.SiteContentKey;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Objects;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * サイト文言 集約
 *
 * <p>
 * サイト名・説明・トップの紹介文など、画面に出る散文をキーで引ける形で保持します。フロントエンドのソースへ
 * 直書きせずデータ側に置くことで、リポジトリからサイトの内容が推測されない状態にします。
 * </p>
 *
 * <p>
 * キー（{@link SiteContentKey}）が自然キーとして機能しますが、ドメインID（{@link Id}）も併せて持ちます。
 * オブジェクトレジストリ（#174）が「全テーブルが {@code domain_id} を持つ」ことを出発点にしているため、
 * ここで持たないとレジストリ導入時にこの集約だけ採番から始めることになります。
 * </p>
 *
 * <p>
 * 文言そのものは {@link MarkupContent} として保持し、描画するかは利用側が決めます（メタタグに使う文言は 描画せず、紹介文は
 * Markdown として描画する）。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class SiteContent implements Aggregate<SiteContent, SiteContent.@NonNull Id> {
    /** サイト文言ID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** どの文言かを指すキー */
    @NonNull
    private final SiteContentKey key;
    /** 文言（本文とマークアップ形式の組） */
    @NonNull
    private final MarkupContent content;

    /** key必須違反時のエラー */
    private static final ErrorResult KEY_REQUIRED_ERROR = new ErrorResult(
            "key",
            "Site content key cannot be null",
            "SITE_CONTENT_KEY_REQUIRED");

    /** content必須違反時のエラー */
    private static final ErrorResult CONTENT_REQUIRED_ERROR = new ErrorResult(
            "content",
            "Site content cannot be null",
            "SITE_CONTENT_REQUIRED");

    @DomainConstructor
    private SiteContent(
            @NonNull Id id,
            @NonNull SiteContentKey key,
            @NonNull MarkupContent content) {
        this.id = id;
        this.key = key;
        this.content = content;
    }

    @DomainFactory
    private static @NonNull SiteContent factory(@Nullable Id id, @Nullable SiteContentKey key,
            @Nullable MarkupContent content) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.key() != null,
                        KEY_REQUIRED_ERROR),
                Policy.of(
                        self -> self.content() != null,
                        CONTENT_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                key,
                                content),
                        Stub::asSiteContent)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, SiteContentKey key, MarkupContent content) {

        @AggregateFactory
        @NonNull
        SiteContent asSiteContent() {
            return new SiteContent(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(key),
                    Objects.requireNonNull(content));
        }
    }

    /**
     * 新規のサイト文言を生成します。
     *
     * @param key
     *            どの文言かを指すキー
     * @param content
     *            文言
     * @return 新規のサイト文言
     */
    @DomainFactory
    public static @NonNull SiteContent create(@NonNull SiteContentKey key, @NonNull MarkupContent content) {
        return SiteContent.factory(
                Id.generate(),
                key,
                content);
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            サイト文言ID
     * @param key
     *            どの文言かを指すキー
     * @param content
     *            文言
     * @return サイト文言
     */
    @DomainFactory
    public static @NonNull SiteContent reconstruct(
            @NonNull Id id,
            @NonNull SiteContentKey key,
            @NonNull MarkupContent content) {
        return SiteContent.factory(
                id,
                key,
                content);
    }

    /**
     * 文言を差し替えます。
     *
     * <p>
     * キーは変えません。キーは「どの文言か」を指す識別であり、差し替えの対象は中身だけです。
     * </p>
     *
     * @param newContent
     *            新しい文言
     * @return 差し替え後のサイト文言
     */
    public @NonNull SiteContent withContent(@NonNull MarkupContent newContent) {
        return SiteContent.factory(
                id,
                key,
                newContent);
    }

    /**
     * サイト文言ID
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<SiteContent> {
        public Id {
            idPolicy(value)
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        private static Policy<String> idPolicy(@Nullable String value) {
            return Policy.all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "Site content ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult(
                                    "value",
                                    "Site content ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")));
        }

        /**
         * UUIDv7を生成して{@code SiteContent.Id}を作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列から{@code SiteContent.Id}を生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }

        /**
         * 外部入力（文字列）から{@code SiteContent.Id}を生成します。
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
}
