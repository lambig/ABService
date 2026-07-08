package com.abservice.domain.model.vo.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("辞退イベント（DeclinedEvent）")
class DeclinedEventTest {

    @DisplayName("単一日付で生成すると各属性が設定され辞退状態になる")
    @Test
    void testCreateWithSingleDate() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        DeclinedEvent event = DeclinedEvent.of("コミックマーケット104", date, DeclineReason.NOT_SELECTED);

        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.declinedDates()).hasSize(1);
        assertThat(event.declinedDates().get(0)).isEqualTo(date);
        assertThat(event.place()).isNull();
        assertThat(event.reason()).isEqualTo(DeclineReason.NOT_SELECTED);
        assertThat(event.isDeclined()).isTrue();
        assertThat(event.isTentative()).isFalse();
        assertThat(event.isSelected()).isFalse();
        assertThat(event.isConfirmed()).isFalse();
    }

    @DisplayName("単一日付と会場を指定して生成できる")
    @Test
    void testCreateWithSingleDateAndPlace() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        DeclinedEvent event = DeclinedEvent.of("地元フェス", date, "市民会館", DeclineReason.CANCELLED_BY_USER);

        assertThat(event.name().value()).isEqualTo("地元フェス");
        assertThat(event.declinedDates()).hasSize(1);
        assertThat(event.place()).isEqualTo("市民会館");
        assertThat(event.reason()).isEqualTo(DeclineReason.CANCELLED_BY_USER);
    }

    @DisplayName("複数日付で生成すると全ての辞退日が保持される")
    @Test
    void testCreateWithMultipleDates() {
        BusinessDate date1 = BusinessDate.of(LocalDate.of(2024, 12, 29));
        BusinessDate date2 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        List<BusinessDate> dates = List.of(date1, date2);

        DeclinedEvent event = DeclinedEvent.of("コミケ", dates, "東京ビッグサイト", DeclineReason.NOT_SELECTED);

        assertThat(event.name().value()).isEqualTo("コミケ");
        assertThat(event.declinedDates()).hasSize(2);
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
        assertThat(event.reason()).isEqualTo(DeclineReason.NOT_SELECTED);
    }

    @DisplayName("応募済みイベントから辞退イベントを生成できる")
    @Test
    void testCreateFromTentative() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        AppliedEvent applied = AppliedEvent.of("M3-2024春", date);

        DeclinedEvent declined = DeclinedEvent.fromApplied(applied, DeclineReason.NOT_SELECTED);

        assertThat(declined.name()).isEqualTo(applied.name());
        assertThat(declined.declinedDates()).hasSize(1);
        assertThat(declined.declinedDates().get(0)).isEqualTo(date);
        assertThat(declined.reason()).isEqualTo(DeclineReason.NOT_SELECTED);
    }

    @DisplayName("日付なしの検討中イベントから辞退生成すると例外になる")
    @Test
    void testCreateFromTentativeWithoutDate() {
        ConsideringEvent considering = ConsideringEvent.of("イベント"); // 日付なし

        assertThatThrownBy(() -> DeclinedEvent.fromTentative(considering, DeclineReason.NOT_SELECTED))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot decline event without dates");
    }

    @DisplayName("名前がnullの場合は例外になる")
    @Test
    void testCreateWithNullName() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        List<BusinessDate> dates = List.of(date);

        assertThatThrownBy(() -> new DeclinedEvent(null, dates, null, DeclineReason.NOT_SELECTED))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be null");
    }

    @DisplayName("辞退日が空の場合は例外になる")
    @Test
    void testCreateWithEmptyDates() {
        assertThatThrownBy(() -> DeclinedEvent.of("イベント", List.of(), null, DeclineReason.NOT_SELECTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Declined event must have at least one declined date");
    }

    @DisplayName("辞退日がnullの場合は例外になる")
    @Test
    void testCreateWithNullDates() {
        assertThatThrownBy(() -> new DeclinedEvent(new EventName("イベント"), null, null, DeclineReason.NOT_SELECTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Declined event must have at least one declined date");
    }

    @DisplayName("辞退理由がnullの場合は例外になる")
    @Test
    void testCreateWithNullReason() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        List<BusinessDate> dates = List.of(date);

        assertThatThrownBy(() -> new DeclinedEvent(new EventName("イベント"), dates, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Decline reason cannot be null");
    }

    @DisplayName("辞退日リストは変更不可である")
    @Test
    void testDeclinedDatesIsUnmodifiable() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        DeclinedEvent event = DeclinedEvent.of("イベント", date, DeclineReason.NOT_SELECTED);

        assertThatThrownBy(() -> event.declinedDates().add(BusinessDate.of(LocalDate.of(2024, 5, 6))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("同一内容の辞退イベント同士は等価と判定される")
    @Test
    void testEquivalentToSame() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        DeclinedEvent event1 = DeclinedEvent.of("イベント", date, "会場", DeclineReason.NOT_SELECTED);
        DeclinedEvent event2 = DeclinedEvent.of("イベント", date, "会場", DeclineReason.NOT_SELECTED);

        assertThat(event1.equivalentTo(event2)).isTrue();
    }

    @DisplayName("辞退理由が異なる場合は等価でないと判定される")
    @Test
    void testEquivalentToDifferentReason() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        DeclinedEvent event1 = DeclinedEvent.of("イベント", date, DeclineReason.NOT_SELECTED);
        DeclinedEvent event2 = DeclinedEvent.of("イベント", date, DeclineReason.CANCELLED_BY_USER);

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @DisplayName("nullとの比較では等価でないと判定される")
    @Test
    void testEquivalentToNull() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        DeclinedEvent event = DeclinedEvent.of("イベント", date, DeclineReason.NOT_SELECTED);

        assertThat(event.equivalentTo(null)).isFalse();
    }

    @DisplayName("辞退理由の表示名が正しく取得できる")
    @Test
    void testDeclineReasonDisplayNames() {
        assertThat(DeclineReason.NOT_SELECTED.displayName()).isEqualTo("落選");
        assertThat(DeclineReason.CANCELLED_BY_USER.displayName()).isEqualTo("キャンセル");
        assertThat(DeclineReason.EVENT_CANCELLED.displayName()).isEqualTo("イベント中止");
    }
}
