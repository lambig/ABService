package com.abservice.domain.model.vo;

import com.abservice.domain.model.vo.common.BusinessDateTime;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDateTimeTest {

    @Test
    void testCreateFromInstant() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        BusinessDateTime businessDateTime = BusinessDateTime.of(instant);

        assertThat(businessDateTime.value()).isEqualTo(instant);
    }

    @Test
    void testAsLocalDateTime() {
        // UTC 2025-01-01 00:00:00 = JST 2025-01-01 09:00:00
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        BusinessDateTime businessDateTime = BusinessDateTime.of(instant);

        LocalDateTime localDateTime = businessDateTime.asLocalDateTime();
        assertThat(localDateTime.getYear()).isEqualTo(2025);
        assertThat(localDateTime.getMonthValue()).isEqualTo(1);
        assertThat(localDateTime.getDayOfMonth()).isEqualTo(1);
        assertThat(localDateTime.getHour()).isEqualTo(9); // JST = UTC+9
    }

    @Test
    void testEquivalentTo() {
        Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        BusinessDateTime dt1 = BusinessDateTime.of(instant);
        BusinessDateTime dt2 = BusinessDateTime.of(instant);

        assertThat(dt1.equivalentTo(dt2)).isTrue();
    }

    @Test
    void testComparable() {
        BusinessDateTime dt1 = BusinessDateTime.of(Instant.parse("2025-01-01T00:00:00Z"));
        BusinessDateTime dt2 = BusinessDateTime.of(Instant.parse("2025-01-02T00:00:00Z"));

        assertThat(dt1.compareTo(dt2)).isLessThan(0);
        assertThat(dt2.compareTo(dt1)).isGreaterThan(0);
    }
}
