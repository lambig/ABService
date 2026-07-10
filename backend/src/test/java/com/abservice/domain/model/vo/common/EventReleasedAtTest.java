package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.event.EventName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventReleasedAt（頒布イベント情報）の生成・同値判定・等価性のテスト")
class EventReleasedAtTest {

    @Test
    @DisplayName("イベント名のみで生成すると、名前が設定され日付・会場・備考は空になる")
    void testCreateWithNameOnly() {
        final EventReleasedAt event = EventReleasedAt.of("コミックマーケット101");

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).isEmpty();
        assertThat(event.place()).isNull();
        assertThat(event.note()).isNull();
    }

    @Test
    @DisplayName("イベント名と日付で生成すると、名前と1件の日付が設定され会場・備考は空になる")
    void testCreateWithNameAndDate() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final EventReleasedAt event = EventReleasedAt.of("コミックマーケット101", date);

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.place()).isNull();
        assertThat(event.note()).isNull();
    }

    @Test
    @DisplayName("イベント名・日付・スペース番号で生成すると、名前と1件の日付・スペース番号が設定される")
    void testCreateWithNameDateAndSpace() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final EventReleasedAt event = EventReleasedAt.of(
                "コミックマーケット101",
                date,
                "東ホ-01a");

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.dateAndSpaces().get(0).spaceNumber()).isEqualTo("東ホ-01a");
    }

    @Test
    @DisplayName("複数の日付・スペースと会場で生成すると、日付が2件設定され会場も設定される")
    void testCreateWithMultipleDates() {
        final BusinessDate date1 = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final BusinessDate date2 = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        31));

        final List<EventDateAndSpace> dateAndSpaces = List
                .of(EventDateAndSpace.of(date1, "東ホ-01a"), EventDateAndSpace.of(date2, "東ホ-01b"));

        final EventReleasedAt event = EventReleasedAt.of(
                "コミックマーケット101",
                dateAndSpaces,
                "東京ビッグサイト",
                null);

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.dateAndSpaces()).hasSize(2);
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
    }

    @Test
    @DisplayName("名前・日付・会場・スペース番号・備考の全情報で生成すると、すべての項目が設定される")
    void testCreateWithAllInformation() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        10,
                        29));
        final EventReleasedAt event = EventReleasedAt.of(
                "M3-2023秋",
                date,
                "東京流通センター",
                "第1展示場A-01a",
                "新譜あります");

        assertThat(event.name().value()).isEqualTo("M3-2023秋");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.dateAndSpaces().get(0).spaceNumber()).isEqualTo("第1展示場A-01a");
        assertThat(event.place()).isEqualTo("東京流通センター");
        assertThat(event.note()).isEqualTo("新譜あります");
    }

    @Test
    @DisplayName("日付・スペースのリストで生成すると、名前・会場・備考が設定され日付リストは変更不可になる")
    void testCreateWithDateAndSpaces() {
        final EventName name = new EventName("テストイベント");
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        5,
                        1));
        final List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "A-01"));

        final EventReleasedAt event = EventReleasedAt.of(
                name.value(),
                dateAndSpaces,
                "会場名",
                "備考");

        assertThat(event.name()).isEqualTo(name);
        assertThat(event.dateAndSpaces()).isUnmodifiable();
        assertThat(event.place()).isEqualTo("会場名");
        assertThat(event.note()).isEqualTo("備考");
    }

    @Test
    @DisplayName("日付・スペースのリストにnullを渡して生成すると、日付リストは空になる")
    void testCreateWithNullDateAndSpaces() {
        final EventName name = new EventName("テストイベント");
        final EventReleasedAt event = EventReleasedAt.of(
                name.value(),
                null,
                "会場名",
                "備考");

        assertThat(event.dateAndSpaces()).isEmpty();
    }

    @Test
    @DisplayName("イベント名にnullを渡して生成すると、IllegalArgumentExceptionがスローされる")
    void testCreateWithNullName() {
        assertThatThrownBy(
                () -> EventReleasedAt.of(
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be blank");
    }

    @Test
    @DisplayName("日付・スペースのリストに要素を追加しようとすると、UnsupportedOperationExceptionがスローされる")
    void testDateAndSpacesIsUnmodifiable() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        5,
                        1));
        final EventReleasedAt event = EventReleasedAt.of(
                "イベント",
                date,
                "A-01");

        assertThatThrownBy(() -> event.dateAndSpaces().add(EventDateAndSpace.of(date, "B-01")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("すべての項目が同一のイベント同士はequivalentToがtrueを返す")
    void testEquivalentToSame() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final EventReleasedAt event1 = EventReleasedAt.of(
                "コミケ101",
                date,
                "東京ビッグサイト",
                "東ホ-01a",
                "備考");
        final EventReleasedAt event2 = EventReleasedAt.of(
                "コミケ101",
                date,
                "東京ビッグサイト",
                "東ホ-01a",
                "備考");

        assertThat(event1.equivalentTo(event2)).isTrue();
    }

    @Test
    @DisplayName("名前が異なるイベント同士はequivalentToがfalseを返す")
    void testEquivalentToDifferentName() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final EventReleasedAt event1 = EventReleasedAt.of("コミケ101", date);
        final EventReleasedAt event2 = EventReleasedAt.of("M3", date);

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    @DisplayName("日付が異なるイベント同士はequivalentToがfalseを返す")
    void testEquivalentToDifferentDate() {
        final BusinessDate date1 = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final BusinessDate date2 = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        31));
        final EventReleasedAt event1 = EventReleasedAt.of("コミケ101", date1);
        final EventReleasedAt event2 = EventReleasedAt.of("コミケ101", date2);

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @Test
    @DisplayName("nullとの比較ではequivalentToがfalseを返す")
    void testEquivalentToNull() {
        final EventReleasedAt event = EventReleasedAt.of("コミケ101");

        assertThat(event.equivalentTo(null)).isFalse();
    }

    @Test
    @DisplayName("全項目が同一のイベントはequalsで等しく、異なるイベントは等しくないと判定される")
    void testEquality() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final EventReleasedAt event1 = EventReleasedAt.of(
                "コミケ101",
                date,
                "A-01");
        final EventReleasedAt event2 = EventReleasedAt.of(
                "コミケ101",
                date,
                "A-01");
        final EventReleasedAt event3 = EventReleasedAt.of(
                "M3",
                date,
                "B-01");

        assertThat(event1).isEqualTo(event2);
        assertThat(event1).isNotEqualTo(event3);
    }

    @Test
    @DisplayName("全項目が同一のイベント同士は同じhashCodeを返す")
    void testHashCode() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final EventReleasedAt event1 = EventReleasedAt.of(
                "コミケ101",
                date,
                "A-01");
        final EventReleasedAt event2 = EventReleasedAt.of(
                "コミケ101",
                date,
                "A-01");

        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }
}
