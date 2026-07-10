package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;

/**
 * クレジット（作曲者・アレンジャー等）の値オブジェクト
 *
 * <p>
 * 作曲者、アレンジャー、その他のクレジット情報を表す値オブジェクトです。 例: "Trad.", "John Doe", "Jane Smith arr."
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * @param value
 *            クレジット
 */
public record Credit(@NonNull String value) implements ValueObject<Credit> {
    /**
     * コンストラクタ
     *
     * @param value
     *            クレジット
     * @throws IllegalArgumentException
     *             クレジットがnullまたは空白の場合、または最大長を超える場合
     */
    public Credit {
        Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult("credit", "Credit cannot be blank", "CREDIT_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= 255,
                        () -> new ErrorResult("credit", "Credit must be 255 characters or less", "CREDIT_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            クレジット
     * @return Creditインスタンス
     */
    public static @NonNull Credit of(@NonNull String value) {
        return new Credit(value);
    }

    @Override
    public boolean equivalentTo(Credit other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
