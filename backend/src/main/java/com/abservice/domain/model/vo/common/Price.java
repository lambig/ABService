package com.abservice.domain.model.vo.common;

import static io.github.lambig.funcifextension.predicate.Predicates.or;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * 価格の値オブジェクト
 *
 * <p>
 * 頒価・DL価格などの金額を表す値オブジェクトです。 日本円を想定し、整数値（円単位）で保持します。
 * </p>
 * <ul>
 * <li>nullは許可されません</li>
 * <li>負の値は許可されません</li>
 * </ul>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(Integer)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(Integer)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param amount
 *            金額（円）
 */
public record Price(Integer amount) implements ValueObject<Price> {
    /**
     * コンストラクタ
     *
     * @param amount
     *            金額（円）
     * @throws IllegalArgumentException
     *             金額がnullまたは負の値の場合
     */
    public Price {
        amountPolicy().verify(amount, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * 無料を表すPriceインスタンスを生成
     *
     * @return 金額0のPriceインスタンス
     */
    public static Price free() {
        return new Price(0);
    }

    /**
     * 金額を指定してPriceインスタンスを生成
     *
     * @param amount
     *            金額（円）
     * @return Priceインスタンス
     */
    public static Price of(Integer amount) {
        return new Price(amount);
    }

    /**
     * 外部入力（数値）から価格を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や負の値は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(Integer)} を使用してください。
     * </p>
     *
     * @param amount
     *            金額（円）を表す数値
     * @return 成功時は {@code Price}、失敗時はエラー
     */
    public static Result<Price> fromInput(@Nullable Integer amount) {
        return amountPolicy().verify(amount, Price::new);
    }

    private static Policy<Integer> amountPolicy() {
        return Policy.<Integer>all(
                Policy.of(
                        Objects::nonNull,
                        () -> new ErrorResult(
                                "amount",
                                "Price amount cannot be null",
                                "AMOUNT_REQUIRED")),
                Policy.of(
                        or(Objects::isNull, (Integer a) -> a >= 0),
                        () -> new ErrorResult(
                                "amount",
                                "Price amount cannot be negative",
                                "AMOUNT_NEGATIVE")));
    }

    /**
     * BigDecimal形式で金額を取得
     *
     * @return 金額（BigDecimal）
     */
    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(amount);
    }

    /**
     * 無料かどうかを判定
     *
     * @return 金額が0の場合true
     */
    public boolean isFree() {
        return amount == 0;
    }

    @Override
    public boolean equivalentTo(Price other) {
        return Optional.ofNullable(other)
                .filter(o -> this.amount.equals(o.amount))
                .isPresent();
    }
}
