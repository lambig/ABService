package com.abservice.domain.model.vo.tune;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TuneTitle値オブジェクト")
class TuneTitleTest {

    @DisplayName("有効なタイトルで生成できる")
    @Test
    void testCreateValidTitle() {
        TuneTitle title = new TuneTitle("Amazing Tune");
        assertThat(title.value()).isEqualTo("Amazing Tune");
    }

    @DisplayName("日本語を含むタイトルで生成できる")
    @Test
    void testCreateTitleWithJapanese() {
        TuneTitle title = new TuneTitle("竹取飛翔 ～ Lunatic Princess");
        assertThat(title.value()).isEqualTo("竹取飛翔 ～ Lunatic Princess");
    }

    @DisplayName("255文字のタイトルで生成できる")
    @Test
    void testCreateTitleMaxLength() {
        String maxLengthTitle = "a".repeat(255);
        TuneTitle title = new TuneTitle(maxLengthTitle);
        assertThat(title.value()).hasSize(255);
    }

    @DisplayName("nullのタイトルは例外を送出する")
    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> new TuneTitle(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @DisplayName("空文字のタイトルは例外を送出する")
    @Test
    void testCreateTitleEmpty() {
        assertThatThrownBy(() -> new TuneTitle("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @DisplayName("空白のみのタイトルは例外を送出する")
    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> new TuneTitle("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @DisplayName("256文字以上のタイトルは例外を送出する")
    @Test
    void testCreateTitleTooLong() {
        String tooLongTitle = "a".repeat(256);
        assertThatThrownBy(() -> new TuneTitle(tooLongTitle)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title must be 255 characters or less");
    }

    @DisplayName("同じ値同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentToSame() {
        TuneTitle title1 = new TuneTitle("Tune Title");
        TuneTitle title2 = new TuneTitle("Tune Title");

        assertThat(title1.equivalentTo(title2)).isTrue();
    }

    @DisplayName("異なる値同士はequivalentToがfalseを返す")
    @Test
    void testEquivalentToDifferent() {
        TuneTitle title1 = new TuneTitle("Tune A");
        TuneTitle title2 = new TuneTitle("Tune B");

        assertThat(title1.equivalentTo(title2)).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalseを返す")
    @Test
    void testEquivalentToNull() {
        TuneTitle title = new TuneTitle("Tune Title");
        assertThat(title.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値は等価で異なる値は非等価となる")
    @Test
    void testEquality() {
        TuneTitle title1 = new TuneTitle("Tune Title");
        TuneTitle title2 = new TuneTitle("Tune Title");
        TuneTitle title3 = new TuneTitle("Different Title");

        assertThat(title1).isEqualTo(title2);
        assertThat(title1).isNotEqualTo(title3);
    }

    @DisplayName("同じ値のhashCodeは一致する")
    @Test
    void testHashCode() {
        TuneTitle title1 = new TuneTitle("Tune Title");
        TuneTitle title2 = new TuneTitle("Tune Title");

        assertThat(title1.hashCode()).isEqualTo(title2.hashCode());
    }
}
