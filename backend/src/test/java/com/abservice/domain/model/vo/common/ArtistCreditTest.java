package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ArtistCredit値オブジェクトのテスト")
class ArtistCreditTest {

    @Test
    @DisplayName("表示名のみで生成するとソートキーは表示名と同じになる")
    void testCreateWithDisplayNameOnly() {
        final ArtistCredit credit = ArtistCredit.of("Foo Bar");

        assertThat(credit.displayName().value()).isEqualTo("Foo Bar");
        assertThat(credit.sortKey()).isEqualTo("Foo Bar");
    }

    @Test
    @DisplayName("表示名とソートキーを指定して生成できる")
    void testCreateWithDisplayNameAndSortKey() {
        final ArtistCredit credit = ArtistCredit.of("Foo Bar feat. Baz", "Foo Bar");

        assertThat(credit.displayName().value()).isEqualTo("Foo Bar feat. Baz");
        assertThat(credit.sortKey()).isEqualTo("Foo Bar");
    }

    @Test
    @DisplayName("ソートキーを指定して生成できる")
    void testCreateWithSortKey() {
        final ArtistCreditName name = new ArtistCreditName("Test Artist");
        final ArtistCredit credit = ArtistCredit.of(name.value(), "Sort Key");

        assertThat(credit.displayName()).isEqualTo(name);
        assertThat(credit.sortKey()).isEqualTo("Sort Key");
    }

    @Test
    @DisplayName("ソートキーがnullの場合は表示名がソートキーになる")
    void testCreateWithNullSortKeyDefaultsToDisplayName() {
        final ArtistCredit credit = ArtistCredit.of("Test Artist", null);

        assertThat(credit.displayName().value()).isEqualTo("Test Artist");
        assertThat(credit.sortKey()).isEqualTo("Test Artist");
    }

    @Test
    @DisplayName("表示名がnullの場合は例外が送出される")
    void testCreateWithNullDisplayName() {
        assertThatThrownBy(() -> ArtistCredit.of(null, "Sort Key")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name cannot be blank");
    }

    @Test
    @DisplayName("同じ内容のArtistCreditは等価と判定される")
    void testEquivalentToSameArtistCredit() {
        final ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        final ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Foo");

        assertThat(credit1.equivalentTo(credit2)).isTrue();
    }

    @Test
    @DisplayName("表示名が異なる場合は等価でないと判定される")
    void testEquivalentToDifferentDisplayName() {
        final ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        final ArtistCredit credit2 = ArtistCredit.of("Baz Qux", "Foo");

        assertThat(credit1.equivalentTo(credit2)).isFalse();
    }

    @Test
    @DisplayName("ソートキーが異なる場合は等価でないと判定される")
    void testEquivalentToDifferentSortKey() {
        final ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        final ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Bar");

        assertThat(credit1.equivalentTo(credit2)).isFalse();
    }

    @Test
    @DisplayName("nullとの比較では等価でないと判定される")
    void testEquivalentToNull() {
        final ArtistCredit credit = ArtistCredit.of("Foo Bar");

        assertThat(credit.equivalentTo(null)).isFalse();
    }

    @Test
    @DisplayName("同じ内容は等しく異なる内容は等しくないと判定される")
    void testEquality() {
        final ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        final ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Foo");
        final ArtistCredit credit3 = ArtistCredit.of("Baz Qux", "Baz");

        assertThat(credit1).isEqualTo(credit2);
        assertThat(credit1).isNotEqualTo(credit3);
    }

    @Test
    @DisplayName("同じ内容のArtistCreditは同じハッシュコードを返す")
    void testHashCode() {
        final ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        final ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Foo");

        assertThat(credit1.hashCode()).isEqualTo(credit2.hashCode());
    }

    @Test
    @DisplayName("日本語の表示名とソートキーを扱える")
    void testJapaneseCharacters() {
        final ArtistCredit credit = ArtistCredit.of("東方アレンジ", "とうほう");

        assertThat(credit.displayName().value()).isEqualTo("東方アレンジ");
        assertThat(credit.sortKey()).isEqualTo("とうほう");
    }

    @Test
    @DisplayName("フィーチャリング表記を含む表示名とソートキーを保持できる")
    void testFeaturedArtist() {
        final ArtistCredit credit = ArtistCredit.of("Artist A feat. Artist B", "Artist A");

        assertThat(credit.displayName().value()).isEqualTo("Artist A feat. Artist B");
        assertThat(credit.sortKey()).isEqualTo("Artist A");
    }
}
