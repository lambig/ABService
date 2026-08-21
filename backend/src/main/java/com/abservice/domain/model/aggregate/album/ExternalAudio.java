package com.abservice.domain.model.aggregate.album;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.DomainConstructor;
import com.abservice.domain.model.DomainFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.entity.DomainEntity;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.ExternalAudioUrl;
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
 * 外部音源（集約内エンティティ）
 *
 * <p>
 * アルバムの試聴を外部サービスの埋め込みで提供するための1件を表します。音源実体は自前配信しないため、保持するのは
 * 埋め込み元URLと、アルバム内での表示順だけです。トラックとの紐付けは持ちません（アルバム単位で並べます）。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ExternalAudio implements DomainEntity<ExternalAudio, ExternalAudio.Id> {
    /** 外部音源ID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** アルバム内での表示順（1, 2, 3, ...） */
    @NonNull
    private final Integer displayOrder;
    /** 埋め込み元URL */
    @NonNull
    private final ExternalAudioUrl url;

    /** displayOrder必須違反時のエラー */
    private static final ErrorResult DISPLAY_ORDER_REQUIRED_ERROR = new ErrorResult(
            "displayOrder",
            "Display order cannot be null",
            "EXTERNAL_AUDIO_DISPLAY_ORDER_REQUIRED");

    /** url必須違反時のエラー */
    private static final ErrorResult URL_REQUIRED_ERROR = new ErrorResult(
            "url",
            "External audio URL cannot be null",
            "EXTERNAL_AUDIO_URL_REQUIRED");

    @DomainConstructor
    private ExternalAudio(
            @NonNull Id id,
            @NonNull Integer displayOrder,
            @NonNull ExternalAudioUrl url) {
        this.id = id;
        this.displayOrder = displayOrder;
        this.url = url;
    }

    @DomainFactory
    private static @NonNull ExternalAudio factory(@Nullable Id id, @Nullable Integer displayOrder,
            @Nullable ExternalAudioUrl url) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.displayOrder() != null,
                        DISPLAY_ORDER_REQUIRED_ERROR),
                Policy.of(
                        self -> self.url() != null,
                        URL_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                displayOrder,
                                url),
                        Stub::asExternalAudio)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, Integer displayOrder, ExternalAudioUrl url) {

        @AggregateFactory
        @NonNull
        ExternalAudio asExternalAudio() {
            return new ExternalAudio(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(displayOrder),
                    Objects.requireNonNull(url));
        }
    }

    /**
     * 新規外部音源を生成
     *
     * @param displayOrder
     *            アルバム内での表示順
     * @param url
     *            埋め込み元URL
     * @return 新規ExternalAudio
     */
    public static @NonNull ExternalAudio create(@NonNull Integer displayOrder, @NonNull ExternalAudioUrl url) {
        return ExternalAudio.factory(
                Id.generate(),
                displayOrder,
                url);
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            外部音源ID
     * @param displayOrder
     *            アルバム内での表示順
     * @param url
     *            埋め込み元URL
     * @return 再構成されたExternalAudio
     */
    @DomainFactory
    public static @NonNull ExternalAudio reconstruct(@NonNull Id id, @NonNull Integer displayOrder,
            @NonNull ExternalAudioUrl url) {
        return ExternalAudio.factory(
                id,
                displayOrder,
                url);
    }

    /**
     * 表示順を変更
     *
     * @param newDisplayOrder
     *            新しい表示順
     * @return 更新されたExternalAudio
     */
    public @NonNull ExternalAudio changeDisplayOrder(@NonNull Integer newDisplayOrder) {
        return ExternalAudio.factory(
                id,
                newDisplayOrder,
                url);
    }

    /**
     * 同じ埋め込み元URLを指しているか
     *
     * @param other
     *            比較対象のURL
     * @return 同一のURLなら true
     */
    public boolean hasUrl(@NonNull ExternalAudioUrl other) {
        return url.equivalentTo(other);
    }

    @Override
    public @NonNull Id id() {
        return id;
    }

    /**
     * ExternalAudio ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<ExternalAudio> {
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
                                    "External audio ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult("value", "External audio ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")));
        }

        /**
         * UUIDv7を生成してExternalAudio.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からExternalAudio.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }

        /**
         * 外部入力（文字列）からExternalAudio.Idを生成します。
         *
         * <p>
         * 例外をスローせず、検証結果を {@link Result} で返します。 信頼できる内部生成には {@link #of(String)}
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
}
