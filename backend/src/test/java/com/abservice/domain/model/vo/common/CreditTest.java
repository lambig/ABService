package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditTest {

    @Test
    void testCreateValidCredit() {
        Credit credit = new Credit("John Doe");
        assertThat(credit.value()).isEqualTo("John Doe");
    }

    @Test
    void testCreateCreditWithSuffix() {
        Credit credit = new Credit("Jane Smith arr.");
        assertThat(credit.value()).isEqualTo("Jane Smith arr.");
    }

    @Test
    void testCreateCreditTraditional() {
        Credit credit = new Credit("Trad.");
        assertThat(credit.value()).isEqualTo("Trad.");
    }

    @Test
    void testCreateCreditWithJapanese() {
        Credit credit = new Credit("ZUN (上海アリス幻樂団)");
        assertThat(credit.value()).isEqualTo("ZUN (上海アリス幻樂団)");
    }

    @Test
    void testCreateCreditMaxLength() {
        String maxLengthCredit = "a".repeat(255);
        Credit credit = new Credit(maxLengthCredit);
        assertThat(credit.value()).hasSize(255);
    }

    @Test
    void testCreateCreditNull() {
        assertThatThrownBy(() -> new Credit(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit cannot be blank");
    }

    @Test
    void testCreateCreditEmpty() {
        assertThatThrownBy(() -> new Credit("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit cannot be blank");
    }

    @Test
    void testCreateCreditBlank() {
        assertThatThrownBy(() -> new Credit("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit cannot be blank");
    }

    @Test
    void testCreateCreditTooLong() {
        String tooLongCredit = "a".repeat(256);
        assertThatThrownBy(() -> new Credit(tooLongCredit)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credit must be 255 characters or less");
    }

    @Test
    void testEquivalentToSame() {
        Credit credit1 = new Credit("John Doe");
        Credit credit2 = new Credit("John Doe");

        assertThat(credit1.equivalentTo(credit2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        Credit credit1 = new Credit("John Doe");
        Credit credit2 = new Credit("Jane Smith");

        assertThat(credit1.equivalentTo(credit2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        Credit credit = new Credit("John Doe");
        assertThat(credit.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        Credit credit1 = new Credit("John Doe");
        Credit credit2 = new Credit("John Doe");
        Credit credit3 = new Credit("Jane Smith");

        assertThat(credit1).isEqualTo(credit2);
        assertThat(credit1).isNotEqualTo(credit3);
    }

    @Test
    void testHashCode() {
        Credit credit1 = new Credit("John Doe");
        Credit credit2 = new Credit("John Doe");

        assertThat(credit1.hashCode()).isEqualTo(credit2.hashCode());
    }
}
