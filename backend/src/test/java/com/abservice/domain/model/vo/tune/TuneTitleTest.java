package com.abservice.domain.model.vo.tune;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TuneTitleTest {

    @Test
    void testCreateValidTitle() {
        TuneTitle title = new TuneTitle("Amazing Tune");
        assertThat(title.value()).isEqualTo("Amazing Tune");
    }

    @Test
    void testCreateTitleWithJapanese() {
        TuneTitle title = new TuneTitle("竹取飛翔 ～ Lunatic Princess");
        assertThat(title.value()).isEqualTo("竹取飛翔 ～ Lunatic Princess");
    }

    @Test
    void testCreateTitleMaxLength() {
        String maxLengthTitle = "a".repeat(255);
        TuneTitle title = new TuneTitle(maxLengthTitle);
        assertThat(title.value()).hasSize(255);
    }

    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> new TuneTitle(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @Test
    void testCreateTitleEmpty() {
        assertThatThrownBy(() -> new TuneTitle("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> new TuneTitle("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title cannot be blank");
    }

    @Test
    void testCreateTitleTooLong() {
        String tooLongTitle = "a".repeat(256);
        assertThatThrownBy(() -> new TuneTitle(tooLongTitle)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tune title must be 255 characters or less");
    }

    @Test
    void testEquivalentToSame() {
        TuneTitle title1 = new TuneTitle("Tune Title");
        TuneTitle title2 = new TuneTitle("Tune Title");

        assertThat(title1.equivalentTo(title2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        TuneTitle title1 = new TuneTitle("Tune A");
        TuneTitle title2 = new TuneTitle("Tune B");

        assertThat(title1.equivalentTo(title2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        TuneTitle title = new TuneTitle("Tune Title");
        assertThat(title.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        TuneTitle title1 = new TuneTitle("Tune Title");
        TuneTitle title2 = new TuneTitle("Tune Title");
        TuneTitle title3 = new TuneTitle("Different Title");

        assertThat(title1).isEqualTo(title2);
        assertThat(title1).isNotEqualTo(title3);
    }

    @Test
    void testHashCode() {
        TuneTitle title1 = new TuneTitle("Tune Title");
        TuneTitle title2 = new TuneTitle("Tune Title");

        assertThat(title1.hashCode()).isEqualTo(title2.hashCode());
    }
}
