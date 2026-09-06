package com.abservice.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageCountsTest {

    @DisplayName("0件とページサイズ境界の総ページ数を維持する")
    @ParameterizedTest
    @CsvSource({"0,20,1", "1,20,1", "19,20,1", "20,20,1", "21,20,2", "40,20,2", "41,20,3", "100,1,100"})
    void preservesPageBoundaries(long count, int size, int expected) {
        assertThat(PageCounts.totalPages(count, size)).isEqualTo(expected);
    }
}
