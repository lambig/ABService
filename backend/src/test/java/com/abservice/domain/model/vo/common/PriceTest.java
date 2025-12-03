package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTest {

    @Test
    void testCreateValidPrice() {
        Price price = new Price(1000);
        assertThat(price.amount()).isEqualTo(1000);
    }

    @Test
    void testCreateZeroPrice() {
        Price price = new Price(0);
        assertThat(price.amount()).isEqualTo(0);
        assertThat(price.isFree()).isTrue();
    }

    @Test
    void testFreeFactoryMethod() {
        Price price = Price.free();
        assertThat(price.amount()).isEqualTo(0);
        assertThat(price.isFree()).isTrue();
    }

    @Test
    void testCreatePriceWithNullAmount() {
        assertThatThrownBy(() -> new Price(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price amount cannot be null");
    }

    @Test
    void testCreatePriceWithNegativeAmount() {
        assertThatThrownBy(() -> new Price(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price amount cannot be negative");
    }

    @Test
    void testToBigDecimal() {
        Price price = new Price(1500);
        BigDecimal bigDecimal = price.toBigDecimal();

        assertThat(bigDecimal).isEqualByComparingTo(BigDecimal.valueOf(1500));
    }

    @Test
    void testIsFreeWhenZero() {
        Price price = new Price(0);
        assertThat(price.isFree()).isTrue();
    }

    @Test
    void testIsFreeWhenNonZero() {
        Price price = new Price(100);
        assertThat(price.isFree()).isFalse();
    }

    @Test
    void testEquivalentToSamePrice() {
        Price price1 = new Price(1000);
        Price price2 = new Price(1000);

        assertThat(price1.equivalentTo(price2)).isTrue();
    }

    @Test
    void testEquivalentToDifferentPrice() {
        Price price1 = new Price(1000);
        Price price2 = new Price(2000);

        assertThat(price1.equivalentTo(price2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        Price price = new Price(1000);
        assertThat(price.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        Price price1 = new Price(1000);
        Price price2 = new Price(1000);
        Price price3 = new Price(2000);

        assertThat(price1).isEqualTo(price2);
        assertThat(price1).isNotEqualTo(price3);
    }

    @Test
    void testHashCode() {
        Price price1 = new Price(1000);
        Price price2 = new Price(1000);

        assertThat(price1.hashCode()).isEqualTo(price2.hashCode());
    }
}
