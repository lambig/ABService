package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ArtistCreditName（アーティストクレジット名）値オブジェクト")
class ArtistCreditNameTest {

    @DisplayName("有効な名前で生成すると値がそのまま保持される")
    @Test
    void testCreateValidName() {
        ArtistCreditName name = new ArtistCreditName("Foo Bar");
        assertThat(name.value()).isEqualTo("Foo Bar");
    }

    @DisplayName("日本語を含む名前で生成すると値がそのまま保持される")
    @Test
    void testCreateNameWithJapanese() {
        ArtistCreditName name = new ArtistCreditName("東方アレンジ");
        assertThat(name.value()).isEqualTo("東方アレンジ");
    }

    @DisplayName("feat.表記を含む名前で生成すると値がそのまま保持される")
    @Test
    void testCreateNameWithFeaturing() {
        ArtistCreditName name = new ArtistCreditName("Artist A feat. Artist B");
        assertThat(name.value()).isEqualTo("Artist A feat. Artist B");
    }

    @DisplayName("最大長255文字の名前で生成できる")
    @Test
    void testCreateNameMaxLength() {
        String maxLengthName = "a".repeat(255);
        ArtistCreditName name = new ArtistCreditName(maxLengthName);
        assertThat(name.value()).hasSize(255);
    }

    @DisplayName("nullで生成すると空白不可の例外が送出される")
    @Test
    void testCreateNameNull() {
        assertThatThrownBy(() -> new ArtistCreditName(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name cannot be blank");
    }

    @DisplayName("空文字で生成すると空白不可の例外が送出される")
    @Test
    void testCreateNameEmpty() {
        assertThatThrownBy(() -> new ArtistCreditName("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name cannot be blank");
    }

    @DisplayName("空白のみの名前で生成すると空白不可の例外が送出される")
    @Test
    void testCreateNameBlank() {
        assertThatThrownBy(() -> new ArtistCreditName("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name cannot be blank");
    }

    @DisplayName("256文字の名前で生成すると255文字以下制限の例外が送出される")
    @Test
    void testCreateNameTooLong() {
        String tooLongName = "a".repeat(256);
        assertThatThrownBy(() -> new ArtistCreditName(tooLongName)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name must be 255 characters or less");
    }

    @DisplayName("同じ値のインスタンス同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentToSame() {
        ArtistCreditName name1 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name2 = new ArtistCreditName("Foo Bar");

        assertThat(name1.equivalentTo(name2)).isTrue();
    }

    @DisplayName("異なる値のインスタンス同士はequivalentToがfalseを返す")
    @Test
    void testEquivalentToDifferent() {
        ArtistCreditName name1 = new ArtistCreditName("Artist A");
        ArtistCreditName name2 = new ArtistCreditName("Artist B");

        assertThat(name1.equivalentTo(name2)).isFalse();
    }

    @DisplayName("nullとの比較ではequivalentToがfalseを返す")
    @Test
    void testEquivalentToNull() {
        ArtistCreditName name = new ArtistCreditName("Foo Bar");
        assertThat(name.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値は等価、異なる値は非等価と判定される")
    @Test
    void testEquality() {
        ArtistCreditName name1 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name2 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name3 = new ArtistCreditName("Different Name");

        assertThat(name1).isEqualTo(name2);
        assertThat(name1).isNotEqualTo(name3);
    }

    @DisplayName("同じ値のインスタンスは同一のhashCodeを返す")
    @Test
    void testHashCode() {
        ArtistCreditName name1 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name2 = new ArtistCreditName("Foo Bar");

        assertThat(name1.hashCode()).isEqualTo(name2.hashCode());
    }
}
