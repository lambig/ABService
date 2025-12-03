package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsrcTest {

    @Test
    void testCreateValidIsrcWithHyphens() {
        Isrc isrc = new Isrc("US-ABC-12-34567");

        assertThat(isrc.value()).isEqualTo("USABC1234567");
        assertThat(isrc.formattedValue()).isEqualTo("US-ABC-12-34567");
    }

    @Test
    void testCreateValidIsrcWithoutHyphens() {
        Isrc isrc = new Isrc("USABC1234567");

        assertThat(isrc.value()).isEqualTo("USABC1234567");
        assertThat(isrc.formattedValue()).isEqualTo("US-ABC-12-34567");
    }

    @Test
    void testCreateValidIsrcLowercaseConverted() {
        Isrc isrc = new Isrc("us-abc-12-34567");

        assertThat(isrc.value()).isEqualTo("USABC1234567");
        assertThat(isrc.formattedValue()).isEqualTo("US-ABC-12-34567");
    }

    @Test
    void testCreateValidIsrcWithSpaces() {
        Isrc isrc = new Isrc("  JP-XYZ-99-88888  ");

        assertThat(isrc.value()).isEqualTo("JPXYZ9988888");
        assertThat(isrc.formattedValue()).isEqualTo("JP-XYZ-99-88888");
    }

    @Test
    void testCreateIsrcWithMixedAlphanumericRegistrantCode() {
        Isrc isrc = new Isrc("GB-A1B-20-12345");

        assertThat(isrc.value()).isEqualTo("GBA1B2012345");
        assertThat(isrc.formattedValue()).isEqualTo("GB-A1B-20-12345");
    }

    @ParameterizedTest
    @ValueSource(strings = {"US-ABC-12", // Too short
            "US-ABC-12-3456", // Too short
            "US-ABC-12-345678", // Too long
            "U-ABC-12-34567", // Country code too short
            "USA-BC-12-34567", // Country code too long
            "12-ABC-12-34567", // Country code not alpha
            "US-AB-12-34567", // Registrant code too short
            "US-ABCD-12-34567", // Registrant code too long
            "US-ABC-1-34567", // Year too short
            "US-ABC-123-34567", // Year too long
            "US-ABC-YY-34567", // Year not numeric
            "US-ABC-12-3456", // Designation code too short
            "US-ABC-12-345678", // Designation code too long
            "US-ABC-12-ABCDE" // Designation code not numeric
    })
    void testCreateIsrcInvalidFormat(String invalidIsrc) {
        assertThatThrownBy(() -> new Isrc(invalidIsrc)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISRC must match the format");
    }

    @Test
    void testCreateIsrcNull() {
        assertThatThrownBy(() -> new Isrc(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ISRC cannot be blank");
    }

    @Test
    void testCreateIsrcBlank() {
        assertThatThrownBy(() -> new Isrc("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ISRC cannot be blank");
    }

    @Test
    void testFormattedValue() {
        Isrc isrc = new Isrc("USABC1234567");

        assertThat(isrc.formattedValue()).isEqualTo("US-ABC-12-34567");
    }

    @Test
    void testEquivalentToSameIsrc() {
        Isrc isrc1 = new Isrc("US-ABC-12-34567");
        Isrc isrc2 = new Isrc("USABC1234567");

        assertThat(isrc1.equivalentTo(isrc2)).isTrue();
    }

    @Test
    void testEquivalentToDifferentIsrc() {
        Isrc isrc1 = new Isrc("US-ABC-12-34567");
        Isrc isrc2 = new Isrc("JP-XYZ-99-88888");

        assertThat(isrc1.equivalentTo(isrc2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        Isrc isrc = new Isrc("US-ABC-12-34567");

        assertThat(isrc.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        Isrc isrc1 = new Isrc("US-ABC-12-34567");
        Isrc isrc2 = new Isrc("USABC1234567");
        Isrc isrc3 = new Isrc("JP-XYZ-99-88888");

        assertThat(isrc1).isEqualTo(isrc2);
        assertThat(isrc1).isNotEqualTo(isrc3);
    }

    @Test
    void testHashCode() {
        Isrc isrc1 = new Isrc("US-ABC-12-34567");
        Isrc isrc2 = new Isrc("USABC1234567");

        assertThat(isrc1.hashCode()).isEqualTo(isrc2.hashCode());
    }
}
