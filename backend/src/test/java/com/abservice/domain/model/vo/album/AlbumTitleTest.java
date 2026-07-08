package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AlbumTitle値オブジェクトのテスト")
class AlbumTitleTest {

    @DisplayName("有効なタイトルで生成できる")
    @Test
    void testCreateValidTitle() {
        final AlbumTitle title = new AlbumTitle("My Awesome Album");
        assertThat(title.value()).isEqualTo("My Awesome Album");
    }

    @DisplayName("日本語のタイトルで生成できる")
    @Test
    void testCreateTitleWithJapanese() {
        final AlbumTitle title = new AlbumTitle("東方アレンジアルバム");
        assertThat(title.value()).isEqualTo("東方アレンジアルバム");
    }

    @DisplayName("最大長255文字のタイトルで生成できる")
    @Test
    void testCreateTitleMaxLength() {
        final String maxLengthTitle = "a".repeat(255);
        final AlbumTitle title = new AlbumTitle(maxLengthTitle);
        assertThat(title.value()).hasSize(255);
    }

    @DisplayName("nullのタイトルは例外となる")
    @Test
    void testCreateTitleNull() {
        assertThatThrownBy(() -> new AlbumTitle(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title cannot be blank");
    }

    @DisplayName("空文字のタイトルは例外となる")
    @Test
    void testCreateTitleEmpty() {
        assertThatThrownBy(() -> new AlbumTitle("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title cannot be blank");
    }

    @DisplayName("空白のみのタイトルは例外となる")
    @Test
    void testCreateTitleBlank() {
        assertThatThrownBy(() -> new AlbumTitle("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title cannot be blank");
    }

    @DisplayName("256文字を超えるタイトルは例外となる")
    @Test
    void testCreateTitleTooLong() {
        final String tooLongTitle = "a".repeat(256);
        assertThatThrownBy(() -> new AlbumTitle(tooLongTitle)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Album title must be 255 characters or less");
    }

    @DisplayName("同じ値同士はequivalentToがtrueとなる")
    @Test
    void testEquivalentToSame() {
        final AlbumTitle title1 = new AlbumTitle("Album Title");
        final AlbumTitle title2 = new AlbumTitle("Album Title");

        assertThat(title1.equivalentTo(title2)).isTrue();
    }

    @DisplayName("異なる値同士はequivalentToがfalseとなる")
    @Test
    void testEquivalentToDifferent() {
        final AlbumTitle title1 = new AlbumTitle("Album A");
        final AlbumTitle title2 = new AlbumTitle("Album B");

        assertThat(title1.equivalentTo(title2)).isFalse();
    }

    @DisplayName("nullとのequivalentToはfalseとなる")
    @Test
    void testEquivalentToNull() {
        final AlbumTitle title = new AlbumTitle("Album Title");
        assertThat(title.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値は等価で異なる値は非等価となる")
    @Test
    void testEquality() {
        final AlbumTitle title1 = new AlbumTitle("Album Title");
        final AlbumTitle title2 = new AlbumTitle("Album Title");
        final AlbumTitle title3 = new AlbumTitle("Different Title");

        assertThat(title1).isEqualTo(title2);
        assertThat(title1).isNotEqualTo(title3);
    }

    @DisplayName("同じ値のhashCodeは一致する")
    @Test
    void testHashCode() {
        final AlbumTitle title1 = new AlbumTitle("Album Title");
        final AlbumTitle title2 = new AlbumTitle("Album Title");

        assertThat(title1.hashCode()).isEqualTo(title2.hashCode());
    }
}
