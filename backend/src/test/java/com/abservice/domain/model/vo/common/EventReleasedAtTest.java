package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.event.EventName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventReleasedAtTest {

    @Test
    void testCreateWithNameOnly() {
        EventReleasedAt event = EventReleasedAt.of("コミックマーケット101");

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).isEmpty();
        assertThat(event.place()).isNull();
        assertThat(event.note()).isNull();
    }

    @Test
    void testCreateWithNameAndDate() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 12, 30));
        EventReleasedAt event = EventReleasedAt.of("コミックマーケット101", date);

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.place()).isNull();
        assertThat(event.note()).isNull();
    }

    @Test
    void testCreateWithNameDateAndSpace() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 12, 30));
        EventReleasedAt event = EventReleasedAt.of("コミックマーケット101", date, "東ホ-01a");

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.dateAndSpaces().get(0).spaceNumber()).isEqualTo("東ホ-01a");
    }

    @Test
    void testCreateWithMultipleDates() {
        BusinessDate date1 = BusinessDate.of(LocalDate.of(2023, 12, 30));
        BusinessDate date2 = BusinessDate.of(LocalDate.of(2023, 12, 31));

        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date1, "東ホ-01a"),
                EventDateAndSpace.of(date2, "東ホ-01b"));

        EventReleasedAt event = EventReleasedAt.of("コミックマーケット101", dateAndSpaces, "東京ビッグサイト", null);

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).hasSize(2);
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
    }

    @Test
    void testCreateWithAllInformation() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 10, 29));
        EventReleasedAt event = EventReleasedAt.of("M3-2023秋", date, "東京流通センター", "第1展示場A-01a", "新譜あります");

        assertThat(event.name().value()).isEqualTo("M3-2023秋");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.dateAndSpaces().get(0).spaceNumber()).isEqualTo("第1展示場A-01a");
        assertThat(event.place()).isEqualTo("東京流通センター");
        assertThat(event.note()).isEqualTo("新譜あります");
    }

    @Test
    void testCreateWithDateAndSpaces() {
        EventName name = new EventName("テストイベント");
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 5, 1));
        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "A-01"));

        EventReleasedAt event = EventReleasedAt.of(name.value(), dateAndSpaces, "会場名", "備考");

        assertThat(event.name()).isEqualTo(name);
        assertThat(event.dateAndSpaces()).isUnmodifiable();
        assertThat(event.place()).isEqualTo("会場名");
        assertThat(event.note()).isEqualTo("備考");
    }

    @Test
    void testCreateWithNullDateAndSpaces() {
        EventName name = new EventName("テストイベント");
        EventReleasedAt event = EventReleasedAt.of(name.value(), null, "会場名", "備考");

        assertThat(event.dateAndSpaces()).isEmpty();
    }

    @Test
    void testCreateWithNullName() {
        assertThatThrownBy(() -> EventReleasedAt.of(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be blank");
    }

    @Test
    void testDateAndSpacesIsUnmodifiable() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 5, 1));
        EventReleasedAt event = EventReleasedAt.of("イベント", date, "A-01");

        assertThatThrownBy(() -> event.dateAndSpaces().add(EventDateAndSpace.of(date, "B-01")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testEquivalentToSame() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 12, 30));
        EventReleasedAt event1 = EventReleasedAt.of("コミケ101", date, "東京ビッグサイト", "東ホ-01a", "備考");
        EventReleasedAt event2 = EventReleasedAt.of("コミケ101", date, "東京ビッグサイト", "東ホ-01a", "備考");

        assertThat(event1.equivalentTo(event2)).isTrue();
    }

    @Test
    void testEquivalentToDifferentName() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 12, 30));
        EventReleasedAt event1 = EventReleasedAt.of("コミケ101", date);
        EventReleasedAt event2 = EventReleasedAt.of("M3", date);

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    void testEquivalentToDifferentDate() {
        BusinessDate date1 = BusinessDate.of(LocalDate.of(2023, 12, 30));
        BusinessDate date2 = BusinessDate.of(LocalDate.of(2023, 12, 31));
        EventReleasedAt event1 = EventReleasedAt.of("コミケ101", date1);
        EventReleasedAt event2 = EventReleasedAt.of("コミケ101", date2);

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        EventReleasedAt event = EventReleasedAt.of("コミケ101");

        assertThat(event.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquality() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 12, 30));
        EventReleasedAt event1 = EventReleasedAt.of("コミケ101", date, "A-01");
        EventReleasedAt event2 = EventReleasedAt.of("コミケ101", date, "A-01");
        EventReleasedAt event3 = EventReleasedAt.of("M3", date, "B-01");

        assertThat(event1).isEqualTo(event2);
        assertThat(event1).isNotEqualTo(event3);
    }

    @Test
    void testHashCode() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2023, 12, 30));
        EventReleasedAt event1 = EventReleasedAt.of("コミケ101", date, "A-01");
        EventReleasedAt event2 = EventReleasedAt.of("コミケ101", date, "A-01");

        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }
}
