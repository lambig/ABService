package com.abservice.domain.model.vo.tune;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;

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
 * @param value
 *            チューンタイトル
 */
public record TuneTitle(@NonNull String value) implements ValueObject<TuneTitle> {
    /**
     * コンストラクタ
     *
     * @param value
     *            チューンタイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public TuneTitle {
        Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "tuneTitle",
                                "Tune title cannot be blank",
                                "TUNE_TITLE_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= 255,
                        () -> new ErrorResult("tuneTitle", "Tune title must be 255 characters or less",
                                "TUNE_TITLE_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            チューンタイトル
     * @return TuneTitleインスタンス
     */
    public static @NonNull TuneTitle of(@NonNull String value) {
        return new TuneTitle(value);
    }

    @Override
    public boolean equivalentTo(TuneTitle other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
