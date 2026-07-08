package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrackTitle値オブジェクト")
class TrackTitleTest {

    @DisplayName("有効なタイトルで生成できる")
    @Test
    void testCreateValidTitle() {
        TrackTitle title = new TrackTitle("Track 01 - Amazing Song");
        assertThat(title.value()).isEqualTo("Track 01 - Amazing Song");
    }

    @DisplayName("日本語のタイトルで生成できる")
    @Test
    void testCreateTitleWithJapanese() {
        TrackTitle title = new TrackTitle("恋色マスタースパーク");
        assertThat(title.value()).isEqualTo("恋色マスタースパーク");
    }

    @DisplayName("最大長255文字のタイトルで生成できる")
    @Test
    void testCreateTitleMaxLength() {
        String maxLengthTitle = "a".repeat(255);
        TrackTitle title = new TrackTitle(maxLengthTitle);
        assertThat(title.value()).hasSize(255);
    }

    @DisplayName("nullの場合は例外を投げる")
    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> new TrackTitle(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title cannot be blank");
    }

    @DisplayName("空文字の場合は例外を投げる")
    @Test
    void testCreateTitleEmpty() {
        assertThatThrownBy(() -> new TrackTitle("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title cannot be blank");
    }

    @DisplayName("空白のみの場合は例外を投げる")
    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> new TrackTitle("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title cannot be blank");
    }

    @DisplayName("256文字以上の場合は例外を投げる")
    @Test
    void testCreateTitleTooLong() {
        String tooLongTitle = "a".repeat(256);
        assertThatThrownBy(() -> new TrackTitle(tooLongTitle)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Track title must be 255 characters or less");
    }

    @DisplayName("同じ値同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentToSame() {
        TrackTitle title1 = new TrackTitle("Track Title");
        TrackTitle title2 = new TrackTitle("Track Title");

        assertThat(title1.equivalentTo(title2)).isTrue();
    }

    @DisplayName("異なる値同士はequivalentToがfalseを返す")
    @Test
    void testEquivalentToDifferent() {
        TrackTitle title1 = new TrackTitle("Track A");
        TrackTitle title2 = new TrackTitle("Track B");

        assertThat(title1.equivalentTo(title2)).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalseを返す")
    @Test
    void testEquivalentToNull() {
        TrackTitle title = new TrackTitle("Track Title");
        assertThat(title.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値は等価で異なる値は非等価となる")
    @Test
    void testEquality() {
        TrackTitle title1 = new TrackTitle("Track Title");
        TrackTitle title2 = new TrackTitle("Track Title");
        TrackTitle title3 = new TrackTitle("Different Title");

        assertThat(title1).isEqualTo(title2);
        assertThat(title1).isNotEqualTo(title3);
    }

    @DisplayName("同じ値は同じhashCodeを返す")
    @Test
    void testHashCode() {
        TrackTitle title1 = new TrackTitle("Track Title");
        TrackTitle title2 = new TrackTitle("Track Title");

        assertThat(title1.hashCode()).isEqualTo(title2.hashCode());
    }
}
