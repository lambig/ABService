package com.abservice.domain.model.vo.tune;

import static java.util.function.Predicate.not;

import com.abservice.domain.model.vo.ValueObject;
import java.util.Optional;
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
        Optional.ofNullable(value).filter(not(String::isBlank))
                .orElseThrow(() -> new IllegalArgumentException("Tune title cannot be blank"));
        if (value.length() > 255) {
            throw new IllegalArgumentException("Tune title must be 255 characters or less");
        }
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
