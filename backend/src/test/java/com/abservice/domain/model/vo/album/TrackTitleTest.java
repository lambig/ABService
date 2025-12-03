package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackTitleTest {

    @Test
    void testCreateValidTitle() {
        TrackTitle title = new TrackTitle("Track 01 - Amazing Song");
        assertThat(title.value()).isEqualTo("Track 01 - Amazing Song");
    }

    @Test
    void testCreateTitleWithJapanese() {
        TrackTitle title = new TrackTitle("恋色マスタースパーク");
        assertThat(title.value()).isEqualTo("恋色マスタースパーク");
    }

    @Test
    void testCreateTitleMaxLength() {
        String maxLengthTitle = "a".repeat(255);
        TrackTitle title = new TrackTitle(maxLengthTitle);
        assertThat(title.value()).hasSize(255);
    }

    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> new TrackTitle(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title cannot be blank");
    }

    @Test
    void testCreateTitleEmpty() {
        assertThatThrownBy(() -> new TrackTitle("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title cannot be blank");
    }

    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> new TrackTitle("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title cannot be blank");
    }

    @Test
    void testCreateTitleTooLong() {
        String tooLongTitle = "a".repeat(256);
        assertThatThrownBy(() -> new TrackTitle(tooLongTitle)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title must be 255 characters or less");
    }

    @Test
    void testEquivalentToSame() {
        TrackTitle title1 = new TrackTitle("Track Title");
        TrackTitle title2 = new TrackTitle("Track Title");

        assertThat(title1.equivalentTo(title2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        TrackTitle title1 = new TrackTitle("Track A");
        TrackTitle title2 = new TrackTitle("Track B");

        assertThat(title1.equivalentTo(title2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        TrackTitle title = new TrackTitle("Track Title");
        assertThat(title.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        TrackTitle title1 = new TrackTitle("Track Title");
        TrackTitle title2 = new TrackTitle("Track Title");
        TrackTitle title3 = new TrackTitle("Different Title");

        assertThat(title1).isEqualTo(title2);
        assertThat(title1).isNotEqualTo(title3);
    }

    @Test
    void testHashCode() {
        TrackTitle title1 = new TrackTitle("Track Title");
        TrackTitle title2 = new TrackTitle("Track Title");

        assertThat(title1.hashCode()).isEqualTo(title2.hashCode());
    }
}
