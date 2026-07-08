package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Price値オブジェクトのテスト")
class PriceTest {

    @DisplayName("正の金額でPriceを生成すると、その金額が保持される")
    @Test
    void testCreateValidPrice() {
        final Price price = new Price(1000);
        assertThat(price.amount()).isEqualTo(1000);
    }

    @DisplayName("金額0でPriceを生成すると、金額が0となり無料と判定される")
    @Test
    void testCreateZeroPrice() {
        final Price price = new Price(0);
        assertThat(price.amount()).isEqualTo(0);
        assertThat(price.isFree()).isTrue();
    }

    @DisplayName("free()ファクトリメソッドは金額0で無料のPriceを生成する")
    @Test
    void testFreeFactoryMethod() {
        final Price price = Price.free();
        assertThat(price.amount()).isEqualTo(0);
        assertThat(price.isFree()).isTrue();
    }

    @DisplayName("金額nullでPriceを生成するとIllegalArgumentExceptionがスローされる")
    @Test
    void testCreatePriceWithNullAmount() {
        assertThatThrownBy(() -> new Price(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price amount cannot be null");
    }

    @DisplayName("負の金額でPriceを生成するとIllegalArgumentExceptionがスローされる")
    @Test
    void testCreatePriceWithNegativeAmount() {
        assertThatThrownBy(() -> new Price(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price amount cannot be negative");
    }

    @DisplayName("toBigDecimalは金額を同値のBigDecimalに変換する")
    @Test
    void testToBigDecimal() {
        final Price price = new Price(1500);
        final BigDecimal bigDecimal = price.toBigDecimal();

        assertThat(bigDecimal).isEqualByComparingTo(BigDecimal.valueOf(1500));
    }

    @DisplayName("金額が0のときisFreeはtrueを返す")
    @Test
    void testIsFreeWhenZero() {
        final Price price = new Price(0);
        assertThat(price.isFree()).isTrue();
    }

    @DisplayName("金額が0でないときisFreeはfalseを返す")
    @Test
    void testIsFreeWhenNonZero() {
        final Price price = new Price(100);
        assertThat(price.isFree()).isFalse();
    }

    @DisplayName("同じ金額のPrice同士のequivalentToはtrueを返す")
    @Test
    void testEquivalentToSamePrice() {
        final Price price1 = new Price(1000);
        final Price price2 = new Price(1000);

        assertThat(price1.equivalentTo(price2)).isTrue();
    }

    @DisplayName("異なる金額のPrice同士のequivalentToはfalseを返す")
    @Test
    void testEquivalentToDifferentPrice() {
        final Price price1 = new Price(1000);
        final Price price2 = new Price(2000);

        assertThat(price1.equivalentTo(price2)).isFalse();
    }

    @DisplayName("nullに対するequivalentToはfalseを返す")
    @Test
    void testEquivalentToNull() {
        final Price price = new Price(1000);
        assertThat(price.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ金額のPriceはequalsで等しく、異なる金額のPriceは等しくない")
    @Test
    void testEquality() {
        final Price price1 = new Price(1000);
        final Price price2 = new Price(1000);
        final Price price3 = new Price(2000);

        assertThat(price1).isEqualTo(price2);
        assertThat(price1).isNotEqualTo(price3);
    }

    @DisplayName("同じ金額のPrice同士のhashCodeは一致する")
    @Test
    void testHashCode() {
        final Price price1 = new Price(1000);
        final Price price2 = new Price(1000);

        assertThat(price1.hashCode()).isEqualTo(price2.hashCode());
    }
}
