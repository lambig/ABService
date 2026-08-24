package com.abservice.infrastructure.persistence.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.application.query.SortDirection;
import com.abservice.application.query.SortSpec;
import io.quarkus.panache.common.Sort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SortOrders（並び順→Panache Sort の組み立て）のテスト")
class SortOrdersTest {

    @Test
    @DisplayName("キーの後ろにタイブレークとしてdomainIdを同じ向きで加える")
    void appendsDomainIdTiebreakWithSameDirection() {
        final var columns = SortOrders.of(new SortSpec("releaseDate", SortDirection.ASC)).getColumns();

        assertThat(columns).hasSize(2);
        assertThat(columns.get(0).getName()).isEqualTo("releaseDate");
        assertThat(columns.get(0).getDirection()).isEqualTo(Sort.Direction.Ascending);
        assertThat(columns.get(1).getName()).isEqualTo("domainId");
        assertThat(columns.get(1).getDirection()).isEqualTo(Sort.Direction.Ascending);
    }

    @Test
    @DisplayName("キー自体がdomainIdならタイブレークを重ねない")
    void doesNotDuplicateDomainId() {
        final var columns = SortOrders.of(SortSpec.defaultOrder()).getColumns();

        assertThat(columns).hasSize(1);
        assertThat(columns.get(0).getName()).isEqualTo("domainId");
        assertThat(columns.get(0).getDirection()).isEqualTo(Sort.Direction.Descending);
    }

    @Test
    @DisplayName("値を持たない行は向きに依らず末尾に置く")
    void putsNullsLastRegardlessOfDirection() {
        final var columns = SortOrders.of(new SortSpec("publishedAt", SortDirection.DESC)).getColumns();

        assertThat(columns)
                .allSatisfy(column -> assertThat(column.getNullPrecedence()).isEqualTo(Sort.NullPrecedence.NULLS_LAST));
    }
}
