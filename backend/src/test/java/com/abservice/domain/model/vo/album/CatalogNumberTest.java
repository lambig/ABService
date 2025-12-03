package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogNumberTest {

    @Test
    void testCreateValidCatalogNumber() {
        CatalogNumber catalog = new CatalogNumber("ABC-0001");
        assertThat(catalog.value()).isEqualTo("ABC-0001");
    }

    @Test
    void testCreateCatalogNumberWithYear() {
        CatalogNumber catalog = new CatalogNumber("XYZ-2024-01");
        assertThat(catalog.value()).isEqualTo("XYZ-2024-01");
    }

    @Test
    void testCreateCatalogNumberAlphanumericOnly() {
        CatalogNumber catalog = new CatalogNumber("ABC123");
        assertThat(catalog.value()).isEqualTo("ABC123");
    }

    @Test
    void testCreateCatalogNumberMaxLength() {
        String maxLengthCatalog = "A".repeat(100);
        CatalogNumber catalog = new CatalogNumber(maxLengthCatalog);
        assertThat(catalog.value()).hasSize(100);
    }

    @Test
    void testCreateCatalogNumberNull() {
        assertThatThrownBy(() -> new CatalogNumber(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number cannot be blank");
    }

    @Test
    void testCreateCatalogNumberEmpty() {
        assertThatThrownBy(() -> new CatalogNumber("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number cannot be blank");
    }

    @Test
    void testCreateCatalogNumberBlank() {
        assertThatThrownBy(() -> new CatalogNumber("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number cannot be blank");
    }

    @Test
    void testCreateCatalogNumberTooLong() {
        String tooLongCatalog = "A".repeat(101);
        assertThatThrownBy(() -> new CatalogNumber(tooLongCatalog)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number must be 100 characters or less");
    }

    @Test
    void testEquivalentToSame() {
        CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        CatalogNumber catalog2 = new CatalogNumber("ABC-0001");

        assertThat(catalog1.equivalentTo(catalog2)).isTrue();
    }

    @Test
    void testEquivalentToDifferent() {
        CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        CatalogNumber catalog2 = new CatalogNumber("XYZ-0002");

        assertThat(catalog1.equivalentTo(catalog2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        CatalogNumber catalog = new CatalogNumber("ABC-0001");
        assertThat(catalog.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        CatalogNumber catalog2 = new CatalogNumber("ABC-0001");
        CatalogNumber catalog3 = new CatalogNumber("XYZ-0002");

        assertThat(catalog1).isEqualTo(catalog2);
        assertThat(catalog1).isNotEqualTo(catalog3);
    }

    @Test
    void testHashCode() {
        CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        CatalogNumber catalog2 = new CatalogNumber("ABC-0001");

        assertThat(catalog1.hashCode()).isEqualTo(catalog2.hashCode());
    }
}
