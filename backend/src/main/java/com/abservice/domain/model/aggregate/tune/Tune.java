package com.abservice.domain.model.aggregate.tune;

import static io.github.lambig.funcifextension.predicate.Predicates.or;

import com.abservice.domain.model.AggregateFactory;
import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import com.abservice.lib.ErrorResult;
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
 * チューン集約
 *
 * <p>
 * セットを構成する個々の「チューン」（曲）を管理します。 トラッド、オリジナル、アレンジの種類を持ちます。
 * </p>
 * <p>
 * 現在は Track から参照される形ですが、将来的にチューンの詳細管理（複数版管理、楽譜、音源など）が
 * 必要になった場合は、完全な集約ルートとして拡張されます。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Tune implements Aggregate<Tune, Tune.@NonNull Id> {
    /** チューンID */
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    /** チューンタイトル */
    @NonNull
    private final TuneTitle title;
    /** チューン種別（トラッド/オリジナル/アレンジ） */
    @NonNull
    private final TuneKind tuneKind;
    /** 作曲者クレジット（デフォルト） */
    @Nullable
    private final Credit defaultComposerCredit;
    /** 編曲者クレジット（デフォルト） */
    @Nullable
    private final Credit defaultArrangerCredit;
    /** {@code tuneKind}がアレンジの場合のみ設定 */
    @Nullable
    private final String originalWorkTitle;
    /** {@code tuneKind}がアレンジの場合のみ設定 */
    @Nullable
    private final String originalWorkCredit;
    /** 例: リール、ジグ */
    @Nullable
    private final String tuneType;
    /** 想定キー */
    @Nullable
    private final String defaultKey;
    /** 単位はBPM */
    @Nullable
    private final Integer defaultTempo;

    /** title必須違反時のエラー */
    private static final ErrorResult TITLE_REQUIRED_ERROR = new ErrorResult(
            "title",
            "Tune title cannot be null",
            "TUNE_TITLE_REQUIRED");

    /** tuneKind必須違反時のエラー */
    private static final ErrorResult KIND_REQUIRED_ERROR = new ErrorResult(
            "tuneKind",
            "Tune kind cannot be null",
            "TUNE_KIND_REQUIRED");

    @SuppressWarnings("checkstyle:ParameterNumber") // PARAM-COUNT: 全フィールドを受け取る唯一の構築経路のため引数が多い
    private Tune(@NonNull Id id, @NonNull TuneTitle title, @NonNull TuneKind tuneKind,
            @Nullable Credit defaultComposerCredit, @Nullable Credit defaultArrangerCredit,
            @Nullable String originalWorkTitle, @Nullable String originalWorkCredit, @Nullable String tuneType,
            @Nullable String defaultKey, @Nullable Integer defaultTempo) {
        this.id = id;
        this.title = title;
        this.tuneKind = tuneKind;
        this.defaultComposerCredit = defaultComposerCredit;
        this.defaultArrangerCredit = defaultArrangerCredit;
        this.originalWorkTitle = originalWorkTitle;
        this.originalWorkCredit = originalWorkCredit;
        this.tuneType = tuneType;
        this.defaultKey = defaultKey;
        this.defaultTempo = defaultTempo;
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // PARAM-COUNT: 全項目を受け取るため引数が多い
    private static @NonNull Tune factory(@Nullable Id id, @Nullable TuneTitle title, @Nullable TuneKind tuneKind,
            @Nullable Credit defaultComposerCredit, @Nullable Credit defaultArrangerCredit,
            @Nullable String originalWorkTitle, @Nullable String originalWorkCredit, @Nullable String tuneType,
            @Nullable String defaultKey, @Nullable Integer defaultTempo) {
        return Policy.<Stub>all(
                Policy.of(
                        self -> self.title() != null,
                        TITLE_REQUIRED_ERROR),
                Policy.of(
                        self -> self.tuneKind() != null,
                        KIND_REQUIRED_ERROR))
                .verify(
                        new Stub(
                                id,
                                title,
                                tuneKind,
                                defaultComposerCredit,
                                defaultArrangerCredit,
                                originalWorkTitle,
                                originalWorkCredit,
                                tuneType,
                                defaultKey,
                                defaultTempo),
                        Stub::asTune)
                .resolve(Policy::illegalArgument);
    }

    @NullUnmarked
    private record Stub(Id id, TuneTitle title, TuneKind tuneKind, Credit defaultComposerCredit,
            Credit defaultArrangerCredit, String originalWorkTitle, String originalWorkCredit, String tuneType,
            String defaultKey, Integer defaultTempo) {

        @AggregateFactory
        @NonNull
        Tune asTune() {
            return new Tune(
                    Objects.requireNonNull(id),
                    Objects.requireNonNull(title),
                    Objects.requireNonNull(tuneKind),
                    defaultComposerCredit(),
                    defaultArrangerCredit(),
                    originalWorkTitle(),
                    originalWorkCredit(),
                    tuneType(),
                    defaultKey(),
                    defaultTempo());
        }
    }

    /**
     * 新規Tuneを生成
     *
     * @param title
     *            タイトル
     * @param tuneKind
     *            チューン種別
     * @param defaultComposerCredit
     *            デフォルト作曲者クレジット
     * @param defaultArrangerCredit
     *            デフォルトアレンジャークレジット
     * @param originalWorkTitle
     *            原曲タイトル（nullable）
     * @param originalWorkCredit
     *            原曲クレジット（nullable）
     * @param tuneType
     *            チューンタイプ（nullable）
     * @param defaultKey
     *            デフォルトキー（nullable）
     * @param defaultTempo
     *            デフォルトテンポ（nullable）
     * @return 新規Tune
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // PARAM-COUNT: 生成に必要な全項目を受け取るため引数が多い
    public static @NonNull Tune create(@NonNull TuneTitle title, @NonNull TuneKind tuneKind,
            @Nullable Credit defaultComposerCredit, @Nullable Credit defaultArrangerCredit,
            @Nullable String originalWorkTitle, @Nullable String originalWorkCredit, @Nullable String tuneType,
            @Nullable String defaultKey, @Nullable Integer defaultTempo) {
        return Tune.factory(
                Id.generate(),
                title,
                tuneKind,
                defaultComposerCredit,
                defaultArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                tuneType,
                defaultKey,
                defaultTempo);
    }

    /**
     * 永続化層からの再構成
     *
     * @param id
     *            ID
     * @param title
     *            タイトル
     * @param tuneKind
     *            チューン種別
     * @param defaultComposerCredit
     *            デフォルト作曲者クレジット
     * @param defaultArrangerCredit
     *            デフォルトアレンジャークレジット
     * @param originalWorkTitle
     *            原曲タイトル（nullable）
     * @param originalWorkCredit
     *            原曲クレジット（nullable）
     * @param tuneType
     *            チューンタイプ（nullable）
     * @param defaultKey
     *            デフォルトキー（nullable）
     * @param defaultTempo
     *            デフォルトテンポ（nullable）
     * @return 再構成されたTune
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // PARAM-COUNT: 永続化からの再構成で全項目を受け取るため引数が多い
    public static @NonNull Tune reconstruct(@NonNull Id id, @NonNull TuneTitle title, @NonNull TuneKind tuneKind,
            @Nullable Credit defaultComposerCredit, @Nullable Credit defaultArrangerCredit,
            @Nullable String originalWorkTitle, @Nullable String originalWorkCredit, @Nullable String tuneType,
            @Nullable String defaultKey, @Nullable Integer defaultTempo) {
        return Tune.factory(
                id,
                title,
                tuneKind,
                defaultComposerCredit,
                defaultArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                tuneType,
                defaultKey,
                defaultTempo);
    }

    /**
     * タイトルを変更
     *
     * @param newTitle
     *            新しいタイトル
     * @return 更新されたTune
     */
    public @NonNull Tune changeTitle(@NonNull TuneTitle newTitle) {
        return Tune.factory(
                id,
                newTitle,
                tuneKind,
                defaultComposerCredit,
                defaultArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                tuneType,
                defaultKey,
                defaultTempo);
    }

    /**
     * デフォルト作曲者クレジットを変更
     *
     * @param newComposerCredit
     *            新しい作曲者クレジット
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultComposerCredit(@Nullable Credit newComposerCredit) {
        return Tune.factory(
                id,
                title,
                tuneKind,
                newComposerCredit,
                defaultArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                tuneType,
                defaultKey,
                defaultTempo);
    }

    /**
     * デフォルトアレンジャークレジットを変更
     *
     * @param newArrangerCredit
     *            新しいアレンジャークレジット
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultArrangerCredit(@Nullable Credit newArrangerCredit) {
        return Tune.factory(
                id,
                title,
                tuneKind,
                defaultComposerCredit,
                newArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                tuneType,
                defaultKey,
                defaultTempo);
    }

    /**
     * 原曲情報を変更
     *
     * @param newOriginalWorkTitle
     *            新しい原曲タイトル
     * @param newOriginalWorkCredit
     *            新しい原曲クレジット
     * @return 更新されたTune
     */
    public @NonNull Tune changeOriginalWorkInfo(@Nullable String newOriginalWorkTitle,
            @Nullable String newOriginalWorkCredit) {
        Policy.<String>of(
                or(unused -> tuneKind != TuneKind.ARRANGEMENT, StringUtils::isNotBlank),
                () -> new ErrorResult("originalWorkTitle", "Original work title is required for ARRANGEMENT tune kind",
                        "ORIGINAL_WORK_TITLE_REQUIRED"))
                .verify(newOriginalWorkTitle, Function.identity())
                .resolve(Policy::illegalArgument);
        return Tune.factory(
                id,
                title,
                tuneKind,
                defaultComposerCredit,
                defaultArrangerCredit,
                newOriginalWorkTitle,
                newOriginalWorkCredit,
                tuneType,
                defaultKey,
                defaultTempo);
    }

    /**
     * チューンタイプを変更
     *
     * @param newTuneType
     *            新しいチューンタイプ（リール、ジグなど）
     * @return 更新されたTune
     */
    public @NonNull Tune changeTuneType(@Nullable String newTuneType) {
        return Tune.factory(
                id,
                title,
                tuneKind,
                defaultComposerCredit,
                defaultArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                newTuneType,
                defaultKey,
                defaultTempo);
    }

    /**
     * デフォルトキーを変更
     *
     * @param newDefaultKey
     *            新しいデフォルトキー
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultKey(@Nullable String newDefaultKey) {
        return Tune.factory(
                id,
                title,
                tuneKind,
                defaultComposerCredit,
                defaultArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                tuneType,
                newDefaultKey,
                defaultTempo);
    }

    /**
     * デフォルトテンポを変更
     *
     * @param newDefaultTempo
     *            新しいデフォルトテンポ（BPM）
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultTempo(@Nullable Integer newDefaultTempo) {
        Policy.<Integer>of(
                or(Objects::isNull, tempo -> tempo > 0),
                () -> new ErrorResult(
                        "defaultTempo",
                        "Tempo must be positive",
                        "TEMPO_MUST_BE_POSITIVE"))
                .verify(newDefaultTempo, Function.identity())
                .resolve(Policy::illegalArgument);
        return Tune.factory(
                id,
                title,
                tuneKind,
                defaultComposerCredit,
                defaultArrangerCredit,
                originalWorkTitle,
                originalWorkCredit,
                tuneType,
                defaultKey,
                newDefaultTempo);
    }

    @Override
    public @NonNull Id id() {
        return id;
    }

    /**
     * Tune ID型
     *
     * @param value
     *            ID値（UUIDv7形式の文字列）
     */
    public record Id(@NonNull String value) implements EntityId<Tune> {
        public Id {
            Policy.<String>all(
                    Policy.of(
                            StringUtils::isNotBlank,
                            () -> new ErrorResult(
                                    "value",
                                    "Tune ID cannot be blank",
                                    "ID_BLANK")),
                    Policy.of(
                            EntityId::isValidUuid,
                            () -> new ErrorResult(
                                    "value",
                                    "Tune ID must be a valid UUID: " + value,
                                    "ID_INVALID_UUID")))
                    .verify(value, Function.identity())
                    .resolve(Policy::illegalArgument);
        }

        /**
         * UUIDv7を生成してTune.Idを作成
         *
         * @return 新規Id
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からTune.Idを生成
         *
         * @param value
         *            ID値（UUIDv7形式の文字列）
         * @return Id
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }
    }
}
