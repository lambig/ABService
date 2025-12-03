package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlbumTitleTest {

    @Test
    void testCreateValidTitle() {
        AlbumTitle title = new AlbumTitle("My Awesome Album");
        assertThat(title.value()).isEqualTo("My Awesome Album");
    }

    @Test
    void testCreateTitleWithJapanese() {
        AlbumTitle title = new AlbumTitle("東方アレンジアルバム");
        assertThat(title.value()).isEqualTo("東方アレンジアルバム");
    }

    @Test
    void testCreateTitleMaxLength() {
        String maxLengthTitle = "a".repeat(255);
        AlbumTitle title = new AlbumTitle(maxLengthTitle);
        assertThat(title.value()).hasSize(255);
    }

    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> new AlbumTitle(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title cannot be blank");
    }

    @Test
    void testCreateTitleEmpty() {
        assertThatThrownBy(() -> new AlbumTitle("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title cannot be blank");
    }

    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> new AlbumTitle("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title cannot be blank");
    }

    @Test
    void testCreateTitleTooLong() {
        String tooLongTitle = "a".repeat(256);
        assertThatThrownBy(() -> new AlbumTitle(tooLongTitle)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title must be 255 characters or less");
    }

    @Test
    void testEquivalentToSame() {
        AlbumTitle title1 = new AlbumTitle("Album Title");
        AlbumTitle title2 = new AlbumTitle("Album Title");

        assertThat(title1.equivalentTo(title2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        AlbumTitle title1 = new AlbumTitle("Album A");
        AlbumTitle title2 = new AlbumTitle("Album B");

        assertThat(title1.equivalentTo(title2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        AlbumTitle title = new AlbumTitle("Album Title");
        assertThat(title.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        AlbumTitle title1 = new AlbumTitle("Album Title");
        AlbumTitle title2 = new AlbumTitle("Album Title");
        AlbumTitle title3 = new AlbumTitle("Different Title");

        assertThat(title1).isEqualTo(title2);
        assertThat(title1).isNotEqualTo(title3);
    }

    @Test
    void testHashCode() {
        AlbumTitle title1 = new AlbumTitle("Album Title");
        AlbumTitle title2 = new AlbumTitle("Album Title");

        assertThat(title1.hashCode()).isEqualTo(title2.hashCode());
    }
}
