package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * トラック上でのチューン名の値オブジェクト
 *
 * <p>
 * トラックのチューン構成が持つチューン名を表します。<b>人が書いた記述</b>であり、
 * {@link com.abservice.domain.model.vo.tune.TuneTitle}（{@code Tune}
 * マスタが持つタイトル）とは別の概念です。 制約が同じでも同定の有無で意味が異なるため、型を分けています。
 * </p>
 *
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            チューン名
 */
public record TrackTuneTitle(@NonNull String value) implements ValueObject<TrackTuneTitle> {
    /** チューン名の最大長 */
    private static final int MAX_LENGTH = 255;

    /**
     * コンストラクタ
     *
     * @param value
     *            チューン名
     * @throws IllegalArgumentException
     *             チューン名がnullまたは空白の場合、または最大長を超える場合
     */
    public TrackTuneTitle {
        titlePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            チューン名
     * @return TrackTuneTitleインスタンス
     */
    public static @NonNull TrackTuneTitle of(@NonNull String value) {
        return new TrackTuneTitle(value);
    }

    /**
     * 外部入力（文字列）からチューン名を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            チューン名を表す文字列
     * @return 成功時は {@code TrackTuneTitle}、失敗時はエラー
     */
    public static Result<TrackTuneTitle> fromInput(@Nullable String value) {
        return titlePolicy().verify(value, TrackTuneTitle::new);
    }

    private static Policy<String> titlePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "tuneTitle",
                                "Tune title cannot be blank",
                                "TRACK_TUNE_TITLE_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult(
                                "tuneTitle",
                                "Tune title must be " + MAX_LENGTH + " characters or less",
                                "TRACK_TUNE_TITLE_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(TrackTuneTitle other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
