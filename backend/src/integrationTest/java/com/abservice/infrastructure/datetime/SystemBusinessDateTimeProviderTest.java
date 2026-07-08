package com.abservice.infrastructure.datetime;

import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.BusinessDateTime;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SystemBusinessDateTimeProviderTest {

    @Inject
    private SystemBusinessDateTimeProvider provider;

    @Test
    void testNow() {
        final Instant before = Instant.now();
        final BusinessDateTime businessDateTime = provider.now().await().indefinitely();
        final Instant after = Instant.now();

        assertThat(businessDateTime.value()).isBetween(before, after);
    }

    @Test
    void testToday() {
        final BusinessDate businessDate = provider.today().await().indefinitely();
        final BusinessDate expected = BusinessDate
                .of(Instant.now().atZone(BusinessDateTime.BUSINESS_ZONE_ID).toLocalDate());

        assertThat(businessDate.value()).isEqualTo(expected.value());
    }
}
