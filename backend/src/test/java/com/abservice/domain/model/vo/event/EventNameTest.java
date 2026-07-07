package com.abservice.domain.model.vo.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("イベント名")
class EventNameTest {

    @DisplayName("有効な名前で生成できる")
    @Test
    void testCreateValidName() {
        EventName name = new EventName("コミックマーケット103");
        assertThat(name.value()).isEqualTo("コミックマーケット103");
    }

    @DisplayName("季節付きの名前で生成できる")
    @Test
    void testCreateNameWithSeason() {
        EventName name = new EventName("M3-2024春");
        assertThat(name.value()).isEqualTo("M3-2024春");
    }

    @DisplayName("最大長255文字の名前で生成できる")
    @Test
    void testCreateNameMaxLength() {
        String maxLengthName = "a".repeat(255);
        EventName name = new EventName(maxLengthName);
        assertThat(name.value()).hasSize(255);
    }

    @DisplayName("nullは空とみなし例外を投げる")
    @Test
    void testCreateNameNull() {
        assertThatThrownBy(() -> new EventName(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name cannot be blank");
    }

    @DisplayName("空文字は例外を投げる")
    @Test
    void testCreateNameEmpty() {
        assertThatThrownBy(() -> new EventName("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name cannot be blank");
    }

    @DisplayName("空白のみは例外を投げる")
    @Test
    void testCreateNameBlank() {
        assertThatThrownBy(() -> new EventName("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name cannot be blank");
    }

    @DisplayName("255文字を超えると例外を投げる")
    @Test
    void testCreateNameTooLong() {
        String tooLongName = "a".repeat(256);
        assertThatThrownBy(() -> new EventName(tooLongName)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name must be 255 characters or less");
    }

    @DisplayName("同じ値はequivalentToがtrue")
    @Test
    void testEquivalentToSame() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("コミケ103");

        assertThat(name1.equivalentTo(name2)).isTrue();
    }

    @DisplayName("異なる値はequivalentToがfalse")
    @Test
    void testEquivalentToDifferent() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("M3-2024春");

        assertThat(name1.equivalentTo(name2)).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalse")
    @Test
    void testEquivalentToNull() {
        EventName name = new EventName("コミケ103");
        assertThat(name.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値は等価で異なる値は非等価")
    @Test
    void testEquality() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("コミケ103");
        EventName name3 = new EventName("M3-2024春");

        assertThat(name1).isEqualTo(name2);
        assertThat(name1).isNotEqualTo(name3);
    }

    @DisplayName("同じ値はhashCodeが一致する")
    @Test
    void testHashCode() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("コミケ103");

        assertThat(name1.hashCode()).isEqualTo(name2.hashCode());
    }
}
