package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistCreditTest {

    @Test
    void testCreateWithDisplayNameOnly() {
        ArtistCredit credit = ArtistCredit.of("Foo Bar");

        assertThat(credit.displayName().value()).isEqualTo("Foo Bar");
        assertThat(credit.sortKey()).isEqualTo("Foo Bar");
    }

    @Test
    void testCreateWithDisplayNameAndSortKey() {
        ArtistCredit credit = ArtistCredit.of("Foo Bar feat. Baz", "Foo Bar");

        assertThat(credit.displayName().value()).isEqualTo("Foo Bar feat. Baz");
        assertThat(credit.sortKey()).isEqualTo("Foo Bar");
    }

    @Test
    void testCreateWithConstructor() {
        ArtistCreditName name = new ArtistCreditName("Test Artist");
        ArtistCredit credit = new ArtistCredit(name, "Sort Key");

        assertThat(credit.displayName()).isEqualTo(name);
        assertThat(credit.sortKey()).isEqualTo("Sort Key");
    }

    @Test
    void testCreateWithConstructorNullSortKey() {
        ArtistCreditName name = new ArtistCreditName("Test Artist");
        ArtistCredit credit = new ArtistCredit(name, null);

        assertThat(credit.displayName()).isEqualTo(name);
        assertThat(credit.sortKey()).isEqualTo("Test Artist");
    }

    @Test
    void testCreateWithNullDisplayName() {
        assertThatThrownBy(() -> new ArtistCredit(null, "Sort Key")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Display name cannot be null");
    }

    @Test
    void testEquivalentToSameArtistCredit() {
        ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Foo");

        assertThat(credit1.equivalentTo(credit2)).isTrue();
    }

    @Test
    void testEquivalentToDifferentDisplayName() {
        ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        ArtistCredit credit2 = ArtistCredit.of("Baz Qux", "Foo");

        assertThat(credit1.equivalentTo(credit2)).isFalse();
    }

    @Test
    void testEquivalentToDifferentSortKey() {
        ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Bar");

        assertThat(credit1.equivalentTo(credit2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        ArtistCredit credit = ArtistCredit.of("Foo Bar");

        assertThat(credit.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Foo");
        ArtistCredit credit3 = ArtistCredit.of("Baz Qux", "Baz");

        assertThat(credit1).isEqualTo(credit2);
        assertThat(credit1).isNotEqualTo(credit3);
    }

    @Test
    void testHashCode() {
        ArtistCredit credit1 = ArtistCredit.of("Foo Bar", "Foo");
        ArtistCredit credit2 = ArtistCredit.of("Foo Bar", "Foo");

        assertThat(credit1.hashCode()).isEqualTo(credit2.hashCode());
    }

    @Test
    void testJapaneseCharacters() {
        ArtistCredit credit = ArtistCredit.of("東方アレンジ", "とうほう");

        assertThat(credit.displayName().value()).isEqualTo("東方アレンジ");
        assertThat(credit.sortKey()).isEqualTo("とうほう");
    }

    @Test
    void testFeaturedArtist() {
        ArtistCredit credit = ArtistCredit.of("Artist A feat. Artist B", "Artist A");

        assertThat(credit.displayName().value()).isEqualTo("Artist A feat. Artist B");
        assertThat(credit.sortKey()).isEqualTo("Artist A");
    }
}
