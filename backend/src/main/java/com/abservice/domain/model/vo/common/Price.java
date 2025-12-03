package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.ValueObject;

import java.math.BigDecimal;
import java.util.Optional;

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
        if (amount == null) {
            throw new IllegalArgumentException("Price amount cannot be null");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Price amount cannot be negative");
        }
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
        return Optional.ofNullable(other).filter(o -> this.amount.equals(o.amount)).isPresent();
    }
}
