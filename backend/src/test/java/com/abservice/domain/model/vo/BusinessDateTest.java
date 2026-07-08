package com.abservice.domain.model.vo;

import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessDate値オブジェクト")
class BusinessDateTest {

    @DisplayName("LocalDateから生成できる")
    @Test
    void testCreateFromLocalDate() {
        final LocalDate localDate = LocalDate.of(2025, 1, 1);
        final BusinessDate businessDate = BusinessDate.of(localDate);

        assertThat(businessDate.value()).isEqualTo(localDate);
    }

    @DisplayName("BusinessDateTimeへ変換できる")
    @Test
    void testAsBusinessDateTime() {
        final LocalDate localDate = LocalDate.of(2025, 1, 1);
        final BusinessDate businessDate = BusinessDate.of(localDate);

        final BusinessDateTime businessDateTime = businessDate.asBusinessDateTime();
        assertThat(businessDateTime.asLocalDate()).isEqualTo(localDate);
    }

    @DisplayName("同じ日付同士はequivalentToがtrueを返す")
    @Test
    void testEquivalentTo() {
        final LocalDate localDate = LocalDate.of(2025, 1, 1);
        final BusinessDate bd1 = BusinessDate.of(localDate);
        final BusinessDate bd2 = BusinessDate.of(localDate);

        assertThat(bd1.equivalentTo(bd2)).isTrue();
    }

    @DisplayName("日付の前後でcompareToが順序を返す")
    @Test
    void testComparable() {
        final BusinessDate bd1 = BusinessDate.of(LocalDate.of(2025, 1, 1));
        final BusinessDate bd2 = BusinessDate.of(LocalDate.of(2025, 1, 2));

        assertThat(bd1.compareTo(bd2)).isLessThan(0);
        assertThat(bd2.compareTo(bd1)).isGreaterThan(0);
    }
}
