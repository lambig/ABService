package com.abservice.domain.model.vo.common;

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
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。クレジット自体が任意（未入力可）の
 * フィールドで使う場合、未入力（{@code null}/空白）の扱いは呼び出し側で判定してください（本メソッドは値が与えられた前提で検証します）。
 * </p>
 *
 * @param value
 *            クレジット
 */
public record Credit(@NonNull String value) implements ValueObject<Credit> {
    /** クレジットの最大長 */
    private static final int MAX_LENGTH = 255;

    /**
     * コンストラクタ
     *
     * @param value
     *            クレジット
     * @throws IllegalArgumentException
     *             クレジットがnullまたは空白の場合、または最大長を超える場合
     */
    public Credit {
        creditPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            クレジット
     * @return Creditインスタンス
     */
    public static @NonNull Credit of(@NonNull String value) {
        return new Credit(value);
    }

    /**
     * 外部入力（文字列）からクレジットを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            クレジットを表す文字列
     * @return 成功時は {@code Credit}、失敗時はエラー
     */
    public static Result<Credit> fromInput(@Nullable String value) {
        return creditPolicy().verify(value, Credit::new);
    }

    private static Policy<String> creditPolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "credit",
                                "Credit cannot be blank",
                                "CREDIT_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult(
                                "credit",
                                "Credit must be " + MAX_LENGTH + " characters or less",
                                "CREDIT_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(Credit other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
