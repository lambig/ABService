package com.abservice.domain.model.vo.album;

import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("頒布額")
class PriceTest {

    @DisplayName("通貨を指定しない生成は円になる")
    @Test
    void testDefaultCurrencyIsYen() {
        final Price price = Price.of(1500);

        assertThat(price.amount()).isEqualTo(1500);
        assertThat(price.currencyCode()).isEqualTo("JPY");
    }

    @DisplayName("通貨を指定して生成できる")
    @Test
    void testCreateWithCurrency() {
        final Price price = Price.of(20, Currency.getInstance("USD"));

        assertThat(price.currencyCode()).isEqualTo("USD");
    }

    @DisplayName("0円は有効な額として扱う")
    @Test
    void testZeroIsValid() {
        assertThat(Price.of(0).amount()).isZero();
    }

    @DisplayName("負の額は例外となる")
    @Test
    void testNegativeAmountThrows() {
        assertThatThrownBy(() -> Price.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price amount cannot be negative");
    }

    @DisplayName("金額がnullの生成は例外となる")
    @Test
    void testNullAmountThrows() {
        assertThatThrownBy(() -> new Price(null, Price.DEFAULT_CURRENCY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price amount cannot be null");
    }

    @DisplayName("通貨がnullの生成は例外となる")
    @Test
    void testNullCurrencyThrows() {
        assertThatThrownBy(() -> new Price(100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price currency cannot be null");
    }

    @DisplayName("外部入力は通貨コードを省略すると円になる")
    @Test
    void testFromInputWithoutCurrency() {
        final Result<Price> result = Price.fromInput(1500, null);

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().currencyCode()).isEqualTo("JPY");
    }

    @DisplayName("外部入力の通貨コードを受け取る")
    @Test
    void testFromInputWithCurrency() {
        assertThat(Price.fromInput(20, "USD").resolve().currencyCode()).isEqualTo("USD");
    }

    @DisplayName("外部入力の金額がnullなら失敗する")
    @Test
    void testFromInputNullAmountFails() {
        final Result<Price> result = Price.fromInput(null, null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Price>) result).errors().getFirst().code())
                .isEqualTo("PRICE_AMOUNT_REQUIRED");
    }

    @DisplayName("外部入力の金額が負なら失敗する")
    @Test
    void testFromInputNegativeAmountFails() {
        final Result<Price> result = Price.fromInput(-1, null);

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Price>) result).errors().getFirst().code())
                .isEqualTo("PRICE_AMOUNT_NEGATIVE");
    }

    @DisplayName("外部入力の通貨コードが未知なら、例外ではなく失敗を返す")
    @Test
    void testFromInputUnknownCurrencyFails() {
        final Result<Price> result = Price.fromInput(100, "XYZ");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(((Result.Failure<Price>) result).errors().getFirst().code())
                .isEqualTo("PRICE_CURRENCY_UNKNOWN");
    }

    @DisplayName("額と通貨が同じなら等価")
    @Test
    void testEquivalence() {
        assertThat(Price.of(1500).equivalentTo(Price.of(1500))).isTrue();
        assertThat(Price.of(1500).equivalentTo(Price.of(1200))).isFalse();
        assertThat(Price.of(1500).equivalentTo(Price.of(1500, Currency.getInstance("USD")))).isFalse();
    }
}
