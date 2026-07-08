package com.abservice.domain.model.vo.album;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("カタログ番号")
class CatalogNumberTest {

    @DisplayName("有効なカタログ番号を生成できる")
    @Test
    void testCreateValidCatalogNumber() {
        final CatalogNumber catalog = new CatalogNumber("ABC-0001");
        assertThat(catalog.value()).isEqualTo("ABC-0001");
    }

    @DisplayName("年号を含むカタログ番号を生成できる")
    @Test
    void testCreateCatalogNumberWithYear() {
        final CatalogNumber catalog = new CatalogNumber("XYZ-2024-01");
        assertThat(catalog.value()).isEqualTo("XYZ-2024-01");
    }

    @DisplayName("英数字のみのカタログ番号を生成できる")
    @Test
    void testCreateCatalogNumberAlphanumericOnly() {
        final CatalogNumber catalog = new CatalogNumber("ABC123");
        assertThat(catalog.value()).isEqualTo("ABC123");
    }

    @DisplayName("最大長100文字のカタログ番号を生成できる")
    @Test
    void testCreateCatalogNumberMaxLength() {
        final String maxLengthCatalog = "A".repeat(100);
        final CatalogNumber catalog = new CatalogNumber(maxLengthCatalog);
        assertThat(catalog.value()).hasSize(100);
    }

    @DisplayName("nullのカタログ番号は例外となる")
    @Test
    void testCreateCatalogNumberNull() {
        assertThatThrownBy(() -> new CatalogNumber(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number cannot be blank");
    }

    @DisplayName("空文字のカタログ番号は例外となる")
    @Test
    void testCreateCatalogNumberEmpty() {
        assertThatThrownBy(() -> new CatalogNumber("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number cannot be blank");
    }

    @DisplayName("空白のみのカタログ番号は例外となる")
    @Test
    void testCreateCatalogNumberBlank() {
        assertThatThrownBy(() -> new CatalogNumber("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number cannot be blank");
    }

    @DisplayName("100文字を超えるカタログ番号は例外となる")
    @Test
    void testCreateCatalogNumberTooLong() {
        final String tooLongCatalog = "A".repeat(101);
        assertThatThrownBy(() -> new CatalogNumber(tooLongCatalog)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Catalog number must be 100 characters or less");
    }

    @DisplayName("同じ値のカタログ番号は同等と判定される")
    @Test
    void testEquivalentToSame() {
        final CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        final CatalogNumber catalog2 = new CatalogNumber("ABC-0001");

        assertThat(catalog1.equivalentTo(catalog2)).isTrue();
    }

    @DisplayName("異なる値のカタログ番号は同等でないと判定される")
    @Test
    void testEquivalentToDifferent() {
        final CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        final CatalogNumber catalog2 = new CatalogNumber("XYZ-0002");

        assertThat(catalog1.equivalentTo(catalog2)).isFalse();
    }

    @DisplayName("nullとの同等判定はfalseとなる")
    @Test
    void testEquivalentToNull() {
        final CatalogNumber catalog = new CatalogNumber("ABC-0001");
        assertThat(catalog.equivalentTo(null)).isFalse();
    }

    @DisplayName("同じ値は等価、異なる値は非等価となる")
    @Test
    void testEquality() {
        final CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        final CatalogNumber catalog2 = new CatalogNumber("ABC-0001");
        final CatalogNumber catalog3 = new CatalogNumber("XYZ-0002");

        assertThat(catalog1).isEqualTo(catalog2);
        assertThat(catalog1).isNotEqualTo(catalog3);
    }

    @DisplayName("同じ値のカタログ番号はhashCodeが一致する")
    @Test
    void testHashCode() {
        final CatalogNumber catalog1 = new CatalogNumber("ABC-0001");
        final CatalogNumber catalog2 = new CatalogNumber("ABC-0001");

        assertThat(catalog1.hashCode()).isEqualTo(catalog2.hashCode());
    }
}
