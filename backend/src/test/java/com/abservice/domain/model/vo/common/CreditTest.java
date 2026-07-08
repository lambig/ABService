package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Credit値オブジェクトのテスト")
class CreditTest {

    @DisplayName("有効な文字列でCreditを生成でき値を取得できる")
    @Test
    void testCreateValidCredit() {
        final Credit credit = new Credit("John Doe");
        assertThat(credit.value()).isEqualTo("John Doe");
    }

    @DisplayName("接尾辞付きの文字列でCreditを生成でき値を取得できる")
    @Test
    void testCreateCreditWithSuffix() {
        final Credit credit = new Credit("Jane Smith arr.");
        assertThat(credit.value()).isEqualTo("Jane Smith arr.");
    }

    @DisplayName("Trad.のような略記の文字列でCreditを生成でき値を取得できる")
    @Test
    void testCreateCreditTraditional() {
        final Credit credit = new Credit("Trad.");
        assertThat(credit.value()).isEqualTo("Trad.");
    }

    @DisplayName("日本語を含む文字列でCreditを生成でき値を取得できる")
    @Test
    void testCreateCreditWithJapanese() {
        final Credit credit = new Credit("ZUN (上海アリス幻樂団)");
        assertThat(credit.value()).isEqualTo("ZUN (上海アリス幻樂団)");
    }

    @DisplayName("255文字の文字列でCreditを生成できる")
    @Test
    void testCreateCreditMaxLength() {
        final String maxLengthCredit = "a".repeat(255);
        final Credit credit = new Credit(maxLengthCredit);
        assertThat(credit.value()).hasSize(255);
    }

    @DisplayName("nullでCreditを生成すると空白不可の例外を送出する")
    @Test
    void testCreateCreditNull() {
        assertThatThrownBy(() -> new Credit(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit cannot be blank");
    }

    @DisplayName("空文字列でCreditを生成すると空白不可の例外を送出する")
    @Test
    void testCreateCreditEmpty() {
        assertThatThrownBy(() -> new Credit("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit cannot be blank");
    }

    @DisplayName("空白のみの文字列でCreditを生成すると空白不可の例外を送出する")
    @Test
    void testCreateCreditBlank() {
        assertThatThrownBy(() -> new Credit("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit cannot be blank");
    }

    @DisplayName("256文字の文字列でCreditを生成すると255文字以下制限の例外を送出する")
    @Test
    void testCreateCreditTooLong() {
        final String tooLongCredit = "a".repeat(256);
        assertThatThrownBy(() -> new Credit(tooLongCredit)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit must be 255 characters or less");
    }

    @DisplayName("同じ値を持つCredit同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentToSame() {
        final Credit credit1 = new Credit("John Doe");
        final Credit credit2 = new Credit("John Doe");

        assertThat(credit1.equivalentTo(credit2)).isTrue();
    }

    @DisplayName("異なる値を持つCredit同士はequivalentToがfalseを返す")
    @Test
    void testEquivalentToDifferent() {
        final Credit credit1 = new Credit("John Doe");
        final Credit credit2 = new Credit("Jane Smith");

        assertThat(credit1.equivalentTo(credit2)).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalseを返す")
    @Test
    void testEquivalentToNull() {
        final Credit credit = new Credit("John Doe");
        assertThat(credit.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値のCreditは等価で異なる値のCreditは非等価と判定される")
    @Test
    void testEquality() {
        final Credit credit1 = new Credit("John Doe");
        final Credit credit2 = new Credit("John Doe");
        final Credit credit3 = new Credit("Jane Smith");

        assertThat(credit1).isEqualTo(credit2);
        assertThat(credit1).isNotEqualTo(credit3);
    }

    @DisplayName("同じ値のCredit同士は同一のhashCodeを返す")
    @Test
    void testHashCode() {
        final Credit credit1 = new Credit("John Doe");
        final Credit credit2 = new Credit("John Doe");

        assertThat(credit1.hashCode()).isEqualTo(credit2.hashCode());
    }
}
