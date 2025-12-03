package com.abservice.domain.model.vo.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import org.junit.jupiter.api.Test;

class ConfirmedEventTest {

    @Test
    void testCreateWithSingleDate() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent event = ConfirmedEvent.of("コミックマーケット104", date, "東ホ-01a");

        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.dateAndSpaces().get(0).spaceNumber()).isEqualTo("東ホ-01a");
        assertThat(event.place()).isNull();
        assertThat(event.isConfirmed()).isTrue();
        assertThat(event.isTentative()).isFalse();
    }

    @Test
    void testCreateWithSingleDateAndPlace() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 10, 27));
        ConfirmedEvent event = ConfirmedEvent.of("M3-2024秋", date, "第1展示場A-01", "東京流通センター");

        assertThat(event.name().value()).isEqualTo("M3-2024秋");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.place()).isEqualTo("東京流通センター");
    }

    @Test
    void testCreateWithMultipleDates() {
        BusinessDate date1 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        BusinessDate date2 = BusinessDate.of(LocalDate.of(2024, 12, 31));
        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date1, "東ホ-01a"),
                EventDateAndSpace.of(date2, "東ホ-01b"));

        ConfirmedEvent event = ConfirmedEvent.of("コミックマーケット104", dateAndSpaces, "東京ビッグサイト");

        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.dateAndSpaces()).hasSize(2);
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
    }

    @Test
    void testCreateFromTentative() {
        AppliedEvent applied = AppliedEvent.of("M3-2024春");
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 4, 28));
        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "第1展示場A-01"));

        ConfirmedEvent confirmed = ConfirmedEvent.fromTentative(applied, dateAndSpaces, "東京流通センター");

        assertThat(confirmed.name()).isEqualTo(applied.name());
        assertThat(confirmed.dateAndSpaces()).hasSize(1);
        assertThat(confirmed.place()).isEqualTo("東京流通センター");
    }

    @Test
    void testCreateWithNullName() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "東ホ-01a"));

        assertThatThrownBy(() -> new ConfirmedEvent(null, dateAndSpaces, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be null");
    }

    @Test
    void testCreateWithEmptyDateAndSpaces() {
        assertThatThrownBy(() -> ConfirmedEvent.of("イベント", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have at least one date and space");
    }

    @Test
    void testCreateWithNullDateAndSpaces() {
        assertThatThrownBy(() -> new ConfirmedEvent(new EventName("イベント"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have at least one date and space");
    }

    @Test
    void testCreateWithMissingSpaceNumber() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, null));

        assertThatThrownBy(() -> new ConfirmedEvent(new EventName("イベント"), dateAndSpaces, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have space number for all dates");
    }

    @Test
    void testCreateWithBlankSpaceNumber() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "  "));

        assertThatThrownBy(() -> new ConfirmedEvent(new EventName("イベント"), dateAndSpaces, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have space number for all dates");
    }

    @Test
    void testDateAndSpacesIsUnmodifiable() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent event = ConfirmedEvent.of("イベント", date, "A-01");

        assertThatThrownBy(() -> event.dateAndSpaces().add(EventDateAndSpace.of(date, "B-01")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testEquivalentToSame() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent event1 = ConfirmedEvent.of("コミケ", date, "東ホ-01a", "東京ビッグサイト");
        ConfirmedEvent event2 = ConfirmedEvent.of("コミケ", date, "東ホ-01a", "東京ビッグサイト");

        assertThat(event1.equivalentTo(event2)).isTrue();
    }

    @Test
    void testEquivalentToDifferentName() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent event1 = ConfirmedEvent.of("コミケ103", date, "東ホ-01a");
        ConfirmedEvent event2 = ConfirmedEvent.of("コミケ104", date, "東ホ-01a");

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    void testEquivalentToDifferentSpace() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent event1 = ConfirmedEvent.of("コミケ", date, "東ホ-01a");
        ConfirmedEvent event2 = ConfirmedEvent.of("コミケ", date, "東ホ-01b");

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent event = ConfirmedEvent.of("イベント", date, "A-01");

        assertThat(event.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquivalentToDifferentType() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        ConfirmedEvent confirmed = ConfirmedEvent.of("M3-2024春", date, "A-01");
        AppliedEvent applied = AppliedEvent.of("M3-2024春", date);

        assertThat(confirmed.equivalentTo(applied)).isFalse();
    }
}
