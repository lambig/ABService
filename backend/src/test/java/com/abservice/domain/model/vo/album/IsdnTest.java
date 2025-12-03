package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsdnTest {

    @Test
    void shouldCreateIsdnWithHyphens() {
        Isdn isdn = new Isdn("278-4-702901-97-8");

        assertThat(isdn.value()).isEqualTo("2784702901978");
        assertThat(isdn.formattedValue()).isEqualTo("278-4-702901-97-8");
    }

    @Test
    void shouldCreateIsdnWithoutHyphens() {
        Isdn isdn = new Isdn("2784702901978");

        assertThat(isdn.value()).isEqualTo("2784702901978");
        assertThat(isdn.formattedValue()).isEqualTo("278-4-702901-97-8");
    }

    @Test
    void shouldNormalizeWhitespace() {
        Isdn isdn = new Isdn("  2784702901978  ");

        assertThat(isdn.value()).isEqualTo("2784702901978");
    }

    @Test
    void shouldAccept279Prefix() {
        // 有効なチェックデジットを持つISDNを使用
        // 279-4-123456-78-0
        Isdn isdn = new Isdn("2794123456780");

        assertThat(isdn.value()).startsWith("2794");
    }

    @ParameterizedTest
    @ValueSource(strings = {"278470290197", // 12桁 (短すぎる)
            "27847029019781", // 14桁 (長すぎる)
            "378-4-702901-97-8", // 不正なフラグ (378)
            "276-4-702901-97-8", // 不正なフラグ (276)
            "278-4-70290A-97-8", // 英字を含む
            "278-4-702901-97-0" // 不正なチェックデジット
    })
    void shouldRejectInvalidFormats(String invalidIsdn) {
        assertThatThrownBy(() -> new Isdn(invalidIsdn)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> new Isdn(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }

    @Test
    void shouldRejectBlank() {
        assertThatThrownBy(() -> new Isdn("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }

    @Test
    void shouldHaveValueSemantics() {
        Isdn isdn = new Isdn("2784702901978");

        assertThat(isdn.value()).isEqualTo("2784702901978");
    }

    @Test
    void shouldBeEquivalentWhenSameValue() {
        Isdn isdn1 = new Isdn("278-4-702901-97-8");
        Isdn isdn2 = new Isdn("2784702901978");

        assertThat(isdn1.equivalentTo(isdn2)).isTrue();
    }

    @Test
    void shouldNotBeEquivalentWhenDifferentValue() {
        Isdn isdn1 = new Isdn("278-4-702901-97-8");
        Isdn isdn2 = new Isdn("2794123456780");

        assertThat(isdn1.equivalentTo(isdn2)).isFalse();
    }

    @Test
    void shouldNotBeEquivalentToNull() {
        Isdn isdn = new Isdn("278-4-702901-97-8");

        assertThat(isdn.equivalentTo(null)).isFalse();
    }

    @Test
    void shouldBeEqualWhenSameValue() {
        Isdn isdn1 = new Isdn("278-4-702901-97-8");
        Isdn isdn2 = new Isdn("2784702901978");
        Isdn isdn3 = new Isdn("2794123456780");

        assertThat(isdn1).isEqualTo(isdn2);
        assertThat(isdn1).isNotEqualTo(isdn3);
    }

    @Test
    void shouldHaveSameHashCodeWhenEquivalent() {
        Isdn isdn1 = new Isdn("278-4-702901-97-8");
        Isdn isdn2 = new Isdn("2784702901978");

        assertThat(isdn1.hashCode()).isEqualTo(isdn2.hashCode());
    }

    @Test
    void shouldValidateCheckDigit() {
        // 有効なチェックデジット
        assertThat(new Isdn("2784702901978").value()).isEqualTo("2784702901978");

        // 無効なチェックデジット
        assertThatThrownBy(() -> new Isdn("2784702901979")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check digit");
    }
}
