package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Isdn")
class IsdnTest {

    @DisplayName("ハイフン付きの値からISDNを生成できる")
    @Test
    void shouldCreateIsdnWithHyphens() {
        final Isdn isdn = new Isdn("278-4-702901-97-8");

        assertThat(isdn.value()).isEqualTo("2784702901978");
        assertThat(isdn.formattedValue()).isEqualTo("278-4-702901-97-8");
    }

    @DisplayName("ハイフンなしの値からISDNを生成できる")
    @Test
    void shouldCreateIsdnWithoutHyphens() {
        final Isdn isdn = new Isdn("2784702901978");

        assertThat(isdn.value()).isEqualTo("2784702901978");
        assertThat(isdn.formattedValue()).isEqualTo("278-4-702901-97-8");
    }

    @DisplayName("前後の空白を除去して正規化する")
    @Test
    void shouldNormalizeWhitespace() {
        final Isdn isdn = new Isdn("  2784702901978  ");

        assertThat(isdn.value()).isEqualTo("2784702901978");
    }

    @DisplayName("279で始まるプレフィックスを受け付ける")
    @Test
    void shouldAccept279Prefix() {
        // 有効なチェックデジットを持つISDNを使用
        // 279-4-123456-78-0
        final Isdn isdn = new Isdn("2794123456780");

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
    @DisplayName("不正な形式の値は例外で拒否する")
    void shouldRejectInvalidFormats(String invalidIsdn) {
        assertThatThrownBy(() -> new Isdn(invalidIsdn)).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("nullは例外で拒否する")
    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> new Isdn(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }

    @DisplayName("空白のみの値は例外で拒否する")
    @Test
    void shouldRejectBlank() {
        assertThatThrownBy(() -> new Isdn("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }

    @DisplayName("値としての意味を保持する")
    @Test
    void shouldHaveValueSemantics() {
        final Isdn isdn = new Isdn("2784702901978");

        assertThat(isdn.value()).isEqualTo("2784702901978");
    }

    @DisplayName("同じ値なら等価と判定する")
    @Test
    void shouldBeEquivalentWhenSameValue() {
        final Isdn isdn1 = new Isdn("278-4-702901-97-8");
        final Isdn isdn2 = new Isdn("2784702901978");

        assertThat(isdn1.equivalentTo(isdn2)).isTrue();
    }

    @DisplayName("値が異なれば等価と判定しない")
    @Test
    void shouldNotBeEquivalentWhenDifferentValue() {
        final Isdn isdn1 = new Isdn("278-4-702901-97-8");
        final Isdn isdn2 = new Isdn("2794123456780");

        assertThat(isdn1.equivalentTo(isdn2)).isFalse();
    }

    @DisplayName("nullとは等価と判定しない")
    @Test
    void shouldNotBeEquivalentToNull() {
        final Isdn isdn = new Isdn("278-4-702901-97-8");

        assertThat(isdn.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値ならequalsで等しく異なる値なら等しくない")
    @Test
    void shouldBeEqualWhenSameValue() {
        final Isdn isdn1 = new Isdn("278-4-702901-97-8");
        final Isdn isdn2 = new Isdn("2784702901978");
        final Isdn isdn3 = new Isdn("2794123456780");

        assertThat(isdn1).isEqualTo(isdn2);
        assertThat(isdn1).isNotEqualTo(isdn3);
    }

    @DisplayName("等価な値同士は同じハッシュコードを持つ")
    @Test
    void shouldHaveSameHashCodeWhenEquivalent() {
        final Isdn isdn1 = new Isdn("278-4-702901-97-8");
        final Isdn isdn2 = new Isdn("2784702901978");

        assertThat(isdn1.hashCode()).isEqualTo(isdn2.hashCode());
    }

    @DisplayName("チェックデジットを検証し不正な場合は例外を投げる")
    @Test
    void shouldValidateCheckDigit() {
        // 有効なチェックデジット
        assertThat(new Isdn("2784702901978").value()).isEqualTo("2784702901978");

        // 無効なチェックデジット
        assertThatThrownBy(() -> new Isdn("2784702901979")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check digit");
    }
}
