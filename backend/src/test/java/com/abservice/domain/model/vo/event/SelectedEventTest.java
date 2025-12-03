package com.abservice.domain.model.vo.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;
import org.junit.jupiter.api.Test;

class SelectedEventTest {

    @Test
    void testCreateWithSingleDate() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        SelectedEvent event = SelectedEvent.of("地元音楽フェス2024", date);

        assertThat(event.name().value()).isEqualTo("地元音楽フェス2024");
        assertThat(event.selectedDates()).hasSize(1);
        assertThat(event.selectedDates().get(0)).isEqualTo(date);
        assertThat(event.place()).isNull();
        assertThat(event.isSelected()).isTrue();
        assertThat(event.isTentative()).isFalse();
        assertThat(event.isConfirmed()).isFalse();
        assertThat(event.isDeclined()).isFalse();
    }

    @Test
    void testCreateWithSingleDateAndPlace() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        SelectedEvent event = SelectedEvent.of("地元音楽フェス2024", date, "市民会館");

        assertThat(event.name().value()).isEqualTo("地元音楽フェス2024");
        assertThat(event.selectedDates()).hasSize(1);
        assertThat(event.place()).isEqualTo("市民会館");
    }

    @Test
    void testCreateWithMultipleDates() {
        BusinessDate date1 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        BusinessDate date2 = BusinessDate.of(LocalDate.of(2024, 12, 31));
        List<BusinessDate> dates = List.of(date1, date2);

        SelectedEvent event = SelectedEvent.of("コミックマーケット104", dates, "東京ビッグサイト");

        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.selectedDates()).hasSize(2);
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
    }

    @Test
    void testCreateFromTentative() {
        AppliedEvent applied = AppliedEvent.of("M3-2024春");
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 4, 28));

        SelectedEvent selected = SelectedEvent.fromApplied(applied, List.of(date), "東京流通センター");

        assertThat(selected.name()).isEqualTo(applied.name());
        assertThat(selected.selectedDates()).hasSize(1);
        assertThat(selected.place()).isEqualTo("東京流通センター");
    }

    @Test
    void testCreateWithNullName() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        List<BusinessDate> dates = List.of(date);

        assertThatThrownBy(() -> new SelectedEvent(null, dates, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be null");
    }

    @Test
    void testCreateWithEmptyDates() {
        assertThatThrownBy(() -> SelectedEvent.of("イベント", List.of(), null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected event must have at least one selected date");
    }

    @Test
    void testCreateWithNullDates() {
        assertThatThrownBy(() -> new SelectedEvent(new EventName("イベント"), null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected event must have at least one selected date");
    }

    @Test
    void testSelectedDatesIsUnmodifiable() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        SelectedEvent event = SelectedEvent.of("イベント", date);

        assertThatThrownBy(() -> event.selectedDates().add(BusinessDate.of(LocalDate.of(2024, 5, 6))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testEquivalentToSame() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        SelectedEvent event1 = SelectedEvent.of("イベント", date, "会場");
        SelectedEvent event2 = SelectedEvent.of("イベント", date, "会場");

        assertThat(event1.equivalentTo(event2)).isTrue();
    }

    @Test
    void testEquivalentToDifferentName() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        SelectedEvent event1 = SelectedEvent.of("イベントA", date);
        SelectedEvent event2 = SelectedEvent.of("イベントB", date);

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    void testEquivalentToDifferentDates() {
        SelectedEvent event1 = SelectedEvent.of("イベント", BusinessDate.of(LocalDate.of(2024, 5, 5)));
        SelectedEvent event2 = SelectedEvent.of("イベント", BusinessDate.of(LocalDate.of(2024, 5, 6)));

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    void testEquivalentToNull() {
        SelectedEvent event = SelectedEvent.of("イベント", BusinessDate.of(LocalDate.of(2024, 5, 5)));

        assertThat(event.equivalentTo(null)).isFalse();
    }

    @Test
    void testEquivalentToDifferentType() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        SelectedEvent selected = SelectedEvent.of("イベント", date);
        AppliedEvent applied = AppliedEvent.of("イベント", date);

        assertThat(selected.equivalentTo(applied)).isFalse();
    }

    @Test
    void testPartialSelection() {
        BusinessDate selectedDate = BusinessDate.of(LocalDate.of(2024, 4, 28));
        BusinessDate declinedDate = BusinessDate.of(LocalDate.of(2024, 4, 29));
        SelectedEvent partialSelected = SelectedEvent.ofPartial("M3-2024春", List.of(selectedDate),
                List.of(declinedDate), "東京流通センター");

        assertThat(partialSelected.isPartialSelection()).isTrue();
        assertThat(partialSelected.isFullSelection()).isFalse();
        assertThat(partialSelected.selectedDates()).hasSize(1);
        assertThat(partialSelected.declinedDates()).hasSize(1);
    }

    @Test
    void testFullSelection() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        SelectedEvent fullSelected = SelectedEvent.of("イベント", date);

        assertThat(fullSelected.isFullSelection()).isTrue();
        assertThat(fullSelected.isPartialSelection()).isFalse();
        assertThat(fullSelected.declinedDates()).isEmpty();
    }

    @Test
    void testFromAppliedPartial() {
        AppliedEvent applied = AppliedEvent.of("M3-2024春");
        BusinessDate selectedDate = BusinessDate.of(LocalDate.of(2024, 4, 28));
        BusinessDate declinedDate = BusinessDate.of(LocalDate.of(2024, 4, 29));

        SelectedEvent partialSelected = SelectedEvent.fromAppliedPartial(applied, List.of(selectedDate),
                List.of(declinedDate), "東京流通センター");

        assertThat(partialSelected.name()).isEqualTo(applied.name());
        assertThat(partialSelected.isPartialSelection()).isTrue();
        assertThat(partialSelected.selectedDates()).hasSize(1);
        assertThat(partialSelected.declinedDates()).hasSize(1);
    }
}
