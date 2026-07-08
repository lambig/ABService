package com.abservice.domain.model.vo.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("選択済みイベント(SelectedEvent)")
class SelectedEventTest {

    @DisplayName("単一日付で生成すると名前・日付が設定され会場はnull、状態は選択済みになる")
    @Test
    void testCreateWithSingleDate() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final SelectedEvent event = SelectedEvent.of("地元音楽フェス2024", date);

        assertThat(event.name().value()).isEqualTo("地元音楽フェス2024");
        assertThat(event.selectedDates()).hasSize(1);
        assertThat(event.selectedDates().get(0)).isEqualTo(date);
        assertThat(event.place()).isNull();
        assertThat(event.isSelected()).isTrue();
        assertThat(event.isTentative()).isFalse();
        assertThat(event.isConfirmed()).isFalse();
        assertThat(event.isDeclined()).isFalse();
    }

    @DisplayName("単一日付と会場で生成すると名前・日付・会場が設定される")
    @Test
    void testCreateWithSingleDateAndPlace() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final SelectedEvent event = SelectedEvent.of("地元音楽フェス2024", date, "市民会館");

        assertThat(event.name().value()).isEqualTo("地元音楽フェス2024");
        assertThat(event.selectedDates()).hasSize(1);
        assertThat(event.place()).isEqualTo("市民会館");
    }

    @DisplayName("複数日付と会場で生成すると全ての日付と会場が設定される")
    @Test
    void testCreateWithMultipleDates() {
        final BusinessDate date1 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final BusinessDate date2 = BusinessDate.of(LocalDate.of(2024, 12, 31));
        final List<BusinessDate> dates = List.of(date1, date2);

        final SelectedEvent event = SelectedEvent.of("コミックマーケット104", dates, "東京ビッグサイト");

        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.selectedDates()).hasSize(2);
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
    }

    @DisplayName("応募済みイベントから生成すると名前が引き継がれ日付・会場が設定される")
    @Test
    void testCreateFromTentative() {
        final AppliedEvent applied = AppliedEvent.of("M3-2024春");
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 4, 28));

        final SelectedEvent selected = SelectedEvent.fromApplied(applied, List.of(date), "東京流通センター");

        assertThat(selected.name()).isEqualTo(applied.name());
        assertThat(selected.selectedDates()).hasSize(1);
        assertThat(selected.place()).isEqualTo("東京流通センター");
    }

    @DisplayName("名前がnullの場合はIllegalArgumentExceptionを送出する")
    @Test
    void testCreateWithNullName() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final List<BusinessDate> dates = List.of(date);

        assertThatThrownBy(() -> new SelectedEvent(null, dates, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be null");
    }

    @DisplayName("選択日付が空の場合はIllegalArgumentExceptionを送出する")
    @Test
    void testCreateWithEmptyDates() {
        assertThatThrownBy(() -> SelectedEvent.of("イベント", List.of(), null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected event must have at least one selected date");
    }

    @DisplayName("選択日付がnullの場合はIllegalArgumentExceptionを送出する")
    @Test
    void testCreateWithNullDates() {
        assertThatThrownBy(() -> new SelectedEvent(new EventName("イベント"), null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected event must have at least one selected date");
    }

    @DisplayName("選択日付リストは変更不可でありaddするとUnsupportedOperationExceptionを送出する")
    @Test
    void testSelectedDatesIsUnmodifiable() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final SelectedEvent event = SelectedEvent.of("イベント", date);

        assertThatThrownBy(() -> event.selectedDates().add(BusinessDate.of(LocalDate.of(2024, 5, 6))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("名前・日付・会場が同一なイベント同士はequivalentToがtrueになる")
    @Test
    void testEquivalentToSame() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final SelectedEvent event1 = SelectedEvent.of("イベント", date, "会場");
        final SelectedEvent event2 = SelectedEvent.of("イベント", date, "会場");

        assertThat(event1.equivalentTo(event2)).isTrue();
    }

    @DisplayName("名前が異なるイベント同士はequivalentToがfalseになる")
    @Test
    void testEquivalentToDifferentName() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final SelectedEvent event1 = SelectedEvent.of("イベントA", date);
        final SelectedEvent event2 = SelectedEvent.of("イベントB", date);

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @DisplayName("日付が異なるイベント同士はequivalentToがfalseになる")
    @Test
    void testEquivalentToDifferentDates() {
        final SelectedEvent event1 = SelectedEvent.of("イベント", BusinessDate.of(LocalDate.of(2024, 5, 5)));
        final SelectedEvent event2 = SelectedEvent.of("イベント", BusinessDate.of(LocalDate.of(2024, 5, 6)));

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @DisplayName("nullとの比較ではequivalentToがfalseになる")
    @Test
    void testEquivalentToNull() {
        final SelectedEvent event = SelectedEvent.of("イベント", BusinessDate.of(LocalDate.of(2024, 5, 5)));

        assertThat(event.equivalentTo(null)).isFalse();
    }

    @DisplayName("型が異なるイベント(AppliedEvent)との比較ではequivalentToがfalseになる")
    @Test
    void testEquivalentToDifferentType() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final SelectedEvent selected = SelectedEvent.of("イベント", date);
        final AppliedEvent applied = AppliedEvent.of("イベント", date);

        assertThat(selected.equivalentTo(applied)).isFalse();
    }

    @DisplayName("一部選択で生成すると部分選択と判定され選択日付と辞退日付が保持される")
    @Test
    void testPartialSelection() {
        final BusinessDate selectedDate = BusinessDate.of(LocalDate.of(2024, 4, 28));
        final BusinessDate declinedDate = BusinessDate.of(LocalDate.of(2024, 4, 29));
        final SelectedEvent partialSelected = SelectedEvent.ofPartial("M3-2024春", List.of(selectedDate),
                List.of(declinedDate), "東京流通センター");

        assertThat(partialSelected.isPartialSelection()).isTrue();
        assertThat(partialSelected.isFullSelection()).isFalse();
        assertThat(partialSelected.selectedDates()).hasSize(1);
        assertThat(partialSelected.declinedDates()).hasSize(1);
    }

    @DisplayName("全選択で生成すると完全選択と判定され辞退日付は空になる")
    @Test
    void testFullSelection() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final SelectedEvent fullSelected = SelectedEvent.of("イベント", date);

        assertThat(fullSelected.isFullSelection()).isTrue();
        assertThat(fullSelected.isPartialSelection()).isFalse();
        assertThat(fullSelected.declinedDates()).isEmpty();
    }

    @DisplayName("応募済みイベントから一部選択で生成すると名前が引き継がれ部分選択と判定される")
    @Test
    void testFromAppliedPartial() {
        final AppliedEvent applied = AppliedEvent.of("M3-2024春");
        final BusinessDate selectedDate = BusinessDate.of(LocalDate.of(2024, 4, 28));
        final BusinessDate declinedDate = BusinessDate.of(LocalDate.of(2024, 4, 29));

        final SelectedEvent partialSelected = SelectedEvent.fromAppliedPartial(applied, List.of(selectedDate),
                List.of(declinedDate), "東京流通センター");

        assertThat(partialSelected.name()).isEqualTo(applied.name());
        assertThat(partialSelected.isPartialSelection()).isTrue();
        assertThat(partialSelected.selectedDates()).hasSize(1);
        assertThat(partialSelected.declinedDates()).hasSize(1);
    }
}
