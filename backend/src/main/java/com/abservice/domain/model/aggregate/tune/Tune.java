package com.abservice.domain.model.aggregate.tune;

import com.abservice.domain.model.EntityId;
import com.abservice.domain.model.aggregate.Aggregate;
import com.abservice.domain.model.vo.common.Credit;
import com.abservice.domain.model.vo.tune.TuneKind;
import com.abservice.domain.model.vo.tune.TuneTitle;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
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
@With(AccessLevel.PRIVATE)
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Tune implements Aggregate<Tune, Tune.@NonNull Id> {
    @EqualsAndHashCode.Include
    @NonNull
    private final Id id;
    @NonNull
    private final TuneTitle title;
    @NonNull
    private final TuneKind tuneKind;
    @Nullable
    private final Credit defaultComposerCredit;
    @Nullable
    private final Credit defaultArrangerCredit;
    @Nullable
    private final String originalWorkTitle; // アレンジの場合の原曲タイトル
    @Nullable
    private final String originalWorkCredit; // アレンジの場合の原曲作曲者・アーティスト
    @Nullable
    private final String tuneType; // リール、ジグなど
    @Nullable
    private final String defaultKey; // 想定キー
    @Nullable
    private final Integer defaultTempo; // BPM

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
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static @NonNull Tune create(@NonNull TuneTitle title, @NonNull TuneKind tuneKind,
            @Nullable Credit defaultComposerCredit, @Nullable Credit defaultArrangerCredit,
            @Nullable String originalWorkTitle, @Nullable String originalWorkCredit, @Nullable String tuneType,
            @Nullable String defaultKey, @Nullable Integer defaultTempo) {
        var validatedTitle = Optional.ofNullable(title)
                .orElseThrow(() -> new IllegalArgumentException("Tune title cannot be null"));
        var validatedKind = Optional.ofNullable(tuneKind)
                .orElseThrow(() -> new IllegalArgumentException("Tune kind cannot be null"));
        return new Tune(Id.generate(), validatedTitle, validatedKind, defaultComposerCredit, defaultArrangerCredit,
                originalWorkTitle, originalWorkCredit, tuneType, defaultKey, defaultTempo);
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
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static @NonNull Tune reconstruct(@NonNull Id id, @NonNull TuneTitle title, @NonNull TuneKind tuneKind,
            @Nullable Credit defaultComposerCredit, @Nullable Credit defaultArrangerCredit,
            @Nullable String originalWorkTitle, @Nullable String originalWorkCredit, @Nullable String tuneType,
            @Nullable String defaultKey, @Nullable Integer defaultTempo) {
        return new Tune(id, title, tuneKind, defaultComposerCredit, defaultArrangerCredit, originalWorkTitle,
                originalWorkCredit, tuneType, defaultKey, defaultTempo);
    }

    /**
     * タイトルを変更
     *
     * @param newTitle
     *            新しいタイトル
     * @return 更新されたTune
     */
    public @NonNull Tune changeTitle(@NonNull TuneTitle newTitle) {
        return withTitle(Optional.ofNullable(newTitle)
                .orElseThrow(() -> new IllegalArgumentException("Tune title cannot be null")));
    }

    /**
     * デフォルト作曲者クレジットを変更
     *
     * @param newComposerCredit
     *            新しい作曲者クレジット
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultComposerCredit(@Nullable Credit newComposerCredit) {
        return withDefaultComposerCredit(newComposerCredit);
    }

    /**
     * デフォルトアレンジャークレジットを変更
     *
     * @param newArrangerCredit
     *            新しいアレンジャークレジット
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultArrangerCredit(@Nullable Credit newArrangerCredit) {
        return withDefaultArrangerCredit(newArrangerCredit);
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
        if (tuneKind == TuneKind.ARRANGEMENT && (newOriginalWorkTitle == null || newOriginalWorkTitle.isBlank())) {
            throw new IllegalArgumentException("Original work title is required for ARRANGEMENT tune kind");
        }
        return withOriginalWorkTitle(newOriginalWorkTitle).withOriginalWorkCredit(newOriginalWorkCredit);
    }

    /**
     * チューンタイプを変更
     *
     * @param newTuneType
     *            新しいチューンタイプ（リール、ジグなど）
     * @return 更新されたTune
     */
    public @NonNull Tune changeTuneType(@Nullable String newTuneType) {
        return withTuneType(newTuneType);
    }

    /**
     * デフォルトキーを変更
     *
     * @param newDefaultKey
     *            新しいデフォルトキー
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultKey(@Nullable String newDefaultKey) {
        return withDefaultKey(newDefaultKey);
    }

    /**
     * デフォルトテンポを変更
     *
     * @param newDefaultTempo
     *            新しいデフォルトテンポ（BPM）
     * @return 更新されたTune
     */
    public @NonNull Tune changeDefaultTempo(@Nullable Integer newDefaultTempo) {
        if (newDefaultTempo != null && newDefaultTempo <= 0) {
            throw new IllegalArgumentException("Tempo must be positive");
        }
        return withDefaultTempo(newDefaultTempo);
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
            Optional.ofNullable(value).filter(v -> !v.isBlank())
                    .orElseThrow(() -> new IllegalArgumentException("Tune ID cannot be blank"));
            if (!EntityId.isValidUuid(value)) {
                throw new IllegalArgumentException("Tune ID must be a valid UUID: " + value);
            }
        }

        /**
         * UUIDv7を生成してTune.Idを作成
         */
        public static @NonNull Id generate() {
            return new Id(EntityId.generateUuidV7());
        }

        /**
         * 文字列からTune.Idを生成
         */
        public static @NonNull Id of(@NonNull String value) {
            return new Id(value);
        }
    }
}
