package com.abservice.domain.model.vo.album;

import static io.github.lambig.funcifextension.predicate.Predicates.or;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.jspecify.annotations.Nullable;

import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 額の値オブジェクト
 *
 * <p>
 * 金額は整数で保持します。扱うのは通貨の最小単位までで、それより下の位を持たないため、十進小数 （{@code BigDecimal}）を必要としません。
 * </p>
 *
 * <p>
 * 額それぞれが自分の通貨を持ち、既定は円です。作品に通貨を1つだけ持たせる形にすると、同一の作品を 複数の国で頒布することを表現できません。
 * </p>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(int)} / {@link #of(int, Currency)}（不正時は例外）を、
 * 外部入力からの生成には {@link #fromInput(Integer, String)}（不正時は {@code Failure}
 * を返す）を使用します。
 * </p>
 *
 * @param amount
 *            金額（通貨の最小単位。円なら1円単位）
 * @param currency
 *            通貨
 */
public record Price(Integer amount, Currency currency) implements ValueObject<Price> {

    /** 既定の通貨 */
    public static final Currency DEFAULT_CURRENCY = Currency.getInstance("JPY");

    /** 金額必須違反時のエラー */
    private static final ErrorResult AMOUNT_REQUIRED_ERROR = new ErrorResult(
            "amount",
            "Price amount cannot be null",
            "PRICE_AMOUNT_REQUIRED");

    /** 金額が負の場合のエラー */
    private static final ErrorResult AMOUNT_NEGATIVE_ERROR = new ErrorResult(
            "amount",
            "Price amount cannot be negative",
            "PRICE_AMOUNT_NEGATIVE");

    /** 通貨必須違反時のエラー */
    private static final ErrorResult CURRENCY_REQUIRED_ERROR = new ErrorResult(
            "currency",
            "Price currency cannot be null",
            "PRICE_CURRENCY_REQUIRED");

    /**
     * コンストラクタ
     *
     * @param amount
     *            金額
     * @param currency
     *            通貨
     * @throws IllegalArgumentException
     *             金額がnullまたは負の値、あるいは通貨がnullの場合
     */
    public Price {
        Policy.<Integer>all(
                Policy.of(
                        Objects::nonNull,
                        AMOUNT_REQUIRED_ERROR),
                Policy.of(
                        or(Objects::isNull, (Integer value) -> value >= 0),
                        AMOUNT_NEGATIVE_ERROR))
                .verify(amount, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        Policy.<Currency>of(
                Objects::nonNull,
                CURRENCY_REQUIRED_ERROR)
                .verify(currency, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * 既定の通貨（円）で頒布額を生成します。
     *
     * @param amount
     *            金額（円）
     * @return Priceインスタンス
     */
    public static Price of(int amount) {
        return new Price(amount, DEFAULT_CURRENCY);
    }

    /**
     * 通貨を指定して頒布額を生成します。
     *
     * @param amount
     *            金額
     * @param currency
     *            通貨
     * @return Priceインスタンス
     */
    public static Price of(int amount, Currency currency) {
        return new Price(amount, currency);
    }

    /**
     * 外部入力から頒布額を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。通貨コードの指定が無ければ既定の通貨 （円）として扱います。
     * </p>
     *
     * @param amount
     *            金額
     * @param currencyCode
     *            通貨コード（ISO 4217。nullなら既定の通貨）
     * @return 成功時は {@code Price}、失敗時はエラー
     */
    public static Result<Price> fromInput(@Nullable Integer amount, @Nullable String currencyCode) {
        return Policy.<Integer>all(
                Policy.of(
                        Objects::nonNull,
                        AMOUNT_REQUIRED_ERROR),
                Policy.of(
                        or(Objects::isNull, (Integer value) -> value >= 0),
                        AMOUNT_NEGATIVE_ERROR))
                .verify(amount, Function.identity())
                .flatMap(
                        value -> currencyOf(currencyCode)
                                .map(currency -> new Price(value, currency)));
    }

    /** 通貨コードの指定が無ければ既定の通貨として扱う */
    private static Result<Currency> currencyOf(@Nullable String currencyCode) {
        return Optional.ofNullable(currencyCode)
                .map(Price::knownCurrency)
                .orElseGet(() -> Result.success(DEFAULT_CURRENCY));
    }

    /*
     * 通貨コードは ISO 4217 の集合に無ければ Currency.getInstance が例外を投げる。外部入力の経路では
     * 例外を投げずにエラーへ畳む。
     */
    private static Result<Currency> knownCurrency(String currencyCode) {
        return Policy.<String>of(
                Price::isKnownCurrency,
                () -> new ErrorResult(
                        "currency",
                        "Price currency is not a known currency code: " + currencyCode,
                        "PRICE_CURRENCY_UNKNOWN"))
                .verify(currencyCode, Currency::getInstance);
    }

    private static boolean isKnownCurrency(String currencyCode) {
        return Currency.getAvailableCurrencies().stream()
                .map(Currency::getCurrencyCode)
                .anyMatch(currencyCode::equals);
    }

    /**
     * 通貨コード（ISO 4217）
     *
     * @return 通貨コード
     */
    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    @Override
    public boolean equivalentTo(Price other) {
        return Optional.ofNullable(other)
                .filter(o -> Objects.equals(this.amount, o.amount))
                .filter(o -> this.currency.equals(o.currency))
                .isPresent();
    }
}
