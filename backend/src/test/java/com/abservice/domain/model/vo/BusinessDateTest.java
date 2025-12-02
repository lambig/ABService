package com.abservice.domain.model.vo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDateTest {

    @Test
    void testCreateFromLocalDate() {
        LocalDate localDate = LocalDate.of(2025, 1, 1);
        BusinessDate businessDate = BusinessDate.of(localDate);

        assertThat(businessDate.value()).isEqualTo(localDate);
    }

    @Test
    void testAsBusinessDateTime() {
        LocalDate localDate = LocalDate.of(2025, 1, 1);
        BusinessDate businessDate = BusinessDate.of(localDate);

        BusinessDateTime businessDateTime = businessDate.asBusinessDateTime();
        assertThat(businessDateTime.asLocalDate()).isEqualTo(localDate);
    }

    @Test
    void testEquivalentTo() {
        LocalDate localDate = LocalDate.of(2025, 1, 1);
        BusinessDate bd1 = BusinessDate.of(localDate);
        BusinessDate bd2 = BusinessDate.of(localDate);

        assertThat(bd1.equivalentTo(bd2)).isTrue();
    }

    @Test
    void testComparable() {
        BusinessDate bd1 = BusinessDate.of(LocalDate.of(2025, 1, 1));
        BusinessDate bd2 = BusinessDate.of(LocalDate.of(2025, 1, 2));

        assertThat(bd1.compareTo(bd2)).isLessThan(0);
        assertThat(bd2.compareTo(bd1)).isGreaterThan(0);
    }
}
