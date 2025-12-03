package com.abservice.domain.model.vo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistCreditNameTest {

    @Test
    void testCreateValidName() {
        ArtistCreditName name = new ArtistCreditName("Foo Bar");
        assertThat(name.value()).isEqualTo("Foo Bar");
    }

    @Test
    void testCreateNameWithJapanese() {
        ArtistCreditName name = new ArtistCreditName("東方アレンジ");
        assertThat(name.value()).isEqualTo("東方アレンジ");
    }

    @Test
    void testCreateNameWithFeaturing() {
        ArtistCreditName name = new ArtistCreditName("Artist A feat. Artist B");
        assertThat(name.value()).isEqualTo("Artist A feat. Artist B");
    }

    @Test
    void testCreateNameMaxLength() {
        String maxLengthName = "a".repeat(255);
        ArtistCreditName name = new ArtistCreditName(maxLengthName);
        assertThat(name.value()).hasSize(255);
    }

    @Test
    void testCreateNameNull() {
        assertThatThrownBy(() -> new ArtistCreditName(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name cannot be blank");
    }

    @Test
    void testCreateNameEmpty() {
        assertThatThrownBy(() -> new ArtistCreditName("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name cannot be blank");
    }

    @Test
    void testCreateNameBlank() {
        assertThatThrownBy(() -> new ArtistCreditName("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name cannot be blank");
    }

    @Test
    void testCreateNameTooLong() {
        String tooLongName = "a".repeat(256);
        assertThatThrownBy(() -> new ArtistCreditName(tooLongName)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Artist credit name must be 255 characters or less");
    }

    @Test
    void testEquivalentToSame() {
        ArtistCreditName name1 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name2 = new ArtistCreditName("Foo Bar");

        assertThat(name1.equivalentTo(name2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        ArtistCreditName name1 = new ArtistCreditName("Artist A");
        ArtistCreditName name2 = new ArtistCreditName("Artist B");

        assertThat(name1.equivalentTo(name2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        ArtistCreditName name = new ArtistCreditName("Foo Bar");
        assertThat(name.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        ArtistCreditName name1 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name2 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name3 = new ArtistCreditName("Different Name");

        assertThat(name1).isEqualTo(name2);
        assertThat(name1).isNotEqualTo(name3);
    }

    @Test
    void testHashCode() {
        ArtistCreditName name1 = new ArtistCreditName("Foo Bar");
        ArtistCreditName name2 = new ArtistCreditName("Foo Bar");

        assertThat(name1.hashCode()).isEqualTo(name2.hashCode());
    }
}
