package com.abservice.domain.model.vo.tune;

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
 * チューンタイトルの値オブジェクト
 *
 * <p>
 * チューン（曲）のタイトルを表す値オブジェクトです。 以下の制約を持ちます：
 * </p>
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
 *            チューンタイトル
 */
public record TuneTitle(@NonNull String value) implements ValueObject<TuneTitle> {
    /** チューンタイトルの最大長 */
    private static final int MAX_LENGTH = 255;

    /**
     * コンストラクタ
     *
     * @param value
     *            チューンタイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public TuneTitle {
        titlePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            チューンタイトル
     * @return TuneTitleインスタンス
     */
    public static @NonNull TuneTitle of(@NonNull String value) {
        return new TuneTitle(value);
    }

    /**
     * 外部入力（文字列）からチューンタイトルを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            チューンタイトルを表す文字列
     * @return 成功時は {@code TuneTitle}、失敗時はエラー
     */
    public static Result<TuneTitle> fromInput(@Nullable String value) {
        return titlePolicy().verify(value, TuneTitle::new);
    }

    private static Policy<String> titlePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "tuneTitle",
                                "Tune title cannot be blank",
                                "TUNE_TITLE_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult("tuneTitle", "Tune title must be " + MAX_LENGTH + " characters or less",
                                "TUNE_TITLE_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(TuneTitle other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
