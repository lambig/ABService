package com.abservice.domain.model.vo;

import com.abservice.domain.model.vo.common.BusinessDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessDateTime値オブジェクト")
class BusinessDateTimeTest {

    @DisplayName("Instantから生成できる")
    @Test
    void testCreateFromInstant() {
        final Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        final BusinessDateTime businessDateTime = BusinessDateTime.of(instant);

        assertThat(businessDateTime.value()).isEqualTo(instant);
    }

    @DisplayName("JST（UTC+9）のLocalDateTimeへ変換できる")
    @Test
    void testAsLocalDateTime() {
        // UTC 2025-01-01 00:00:00 = JST 2025-01-01 09:00:00
        final Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        final BusinessDateTime businessDateTime = BusinessDateTime.of(instant);

        final LocalDateTime localDateTime = businessDateTime.asLocalDateTime();
        assertThat(localDateTime.getYear()).isEqualTo(2025);
        assertThat(localDateTime.getMonthValue()).isEqualTo(1);
        assertThat(localDateTime.getDayOfMonth()).isEqualTo(1);
        assertThat(localDateTime.getHour()).isEqualTo(9); // JST = UTC+9
    }

    @DisplayName("同じ日時同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentTo() {
        final Instant instant = Instant.parse("2025-01-01T00:00:00Z");
        final BusinessDateTime dt1 = BusinessDateTime.of(instant);
        final BusinessDateTime dt2 = BusinessDateTime.of(instant);

        assertThat(dt1.equivalentTo(dt2)).isTrue();
    }

    @DisplayName("日時の前後でcompareToが順序を返す")
    @Test
    void testComparable() {
        final BusinessDateTime dt1 = BusinessDateTime.of(Instant.parse("2025-01-01T00:00:00Z"));
        final BusinessDateTime dt2 = BusinessDateTime.of(Instant.parse("2025-01-02T00:00:00Z"));

        assertThat(dt1.compareTo(dt2)).isLessThan(0);
        assertThat(dt2.compareTo(dt1)).isGreaterThan(0);
    }
}
