package com.abservice.domain.model.vo.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventNameTest {

    @Test
    void testCreateValidName() {
        EventName name = new EventName("コミックマーケット103");
        assertThat(name.value()).isEqualTo("コミックマーケット103");
    }

    @Test
    void testCreateNameWithSeason() {
        EventName name = new EventName("M3-2024春");
        assertThat(name.value()).isEqualTo("M3-2024春");
    }

    @Test
    void testCreateNameMaxLength() {
        String maxLengthName = "a".repeat(255);
        EventName name = new EventName(maxLengthName);
        assertThat(name.value()).hasSize(255);
    }

    @Test
    void testCreateNameNull() {
        assertThatThrownBy(() -> new EventName(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name cannot be blank");
    }

    @Test
    void testCreateNameEmpty() {
        assertThatThrownBy(() -> new EventName("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name cannot be blank");
    }

    @Test
    void testCreateNameBlank() {
        assertThatThrownBy(() -> new EventName("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name cannot be blank");
    }

    @Test
    void testCreateNameTooLong() {
        String tooLongName = "a".repeat(256);
        assertThatThrownBy(() -> new EventName(tooLongName)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event name must be 255 characters or less");
    }

    @Test
    void testEquivalentToSame() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("コミケ103");

        assertThat(name1.equivalentTo(name2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("M3-2024春");

        assertThat(name1.equivalentTo(name2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        EventName name = new EventName("コミケ103");
        assertThat(name.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("コミケ103");
        EventName name3 = new EventName("M3-2024春");

        assertThat(name1).isEqualTo(name2);
        assertThat(name1).isNotEqualTo(name3);
    }

    @Test
    void testHashCode() {
        EventName name1 = new EventName("コミケ103");
        EventName name2 = new EventName("コミケ103");

        assertThat(name1.hashCode()).isEqualTo(name2.hashCode());
    }
}
