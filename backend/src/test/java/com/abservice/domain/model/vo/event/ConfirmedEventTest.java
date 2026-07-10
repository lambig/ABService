package com.abservice.domain.model.vo.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("確定イベント(ConfirmedEvent)")
class ConfirmedEventTest {

    @DisplayName("単一日程で生成すると名前・日程・スペースが設定され確定状態になる")
    @Test
    void testCreateWithSingleDate() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final ConfirmedEvent event = ConfirmedEvent.of("コミックマーケット104", date, "東ホ-01a");

        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.dateAndSpaces().get(0).date()).isEqualTo(date);
        assertThat(event.dateAndSpaces().get(0).spaceNumber()).isEqualTo("東ホ-01a");
        assertThat(event.place()).isNull();
        assertThat(event.isConfirmed()).isTrue();
        assertThat(event.isTentative()).isFalse();
    }

    @DisplayName("会場指定付きの単一日程で生成すると会場が設定される")
    @Test
    void testCreateWithSingleDateAndPlace() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 10, 27));
        final ConfirmedEvent event = ConfirmedEvent.of("M3-2024秋", date, "第1展示場A-01", "東京流通センター");

        assertThat(event.name().value()).isEqualTo("M3-2024秋");
        assertThat(event.dateAndSpaces()).hasSize(1);
        assertThat(event.place()).isEqualTo("東京流通センター");
    }

    @DisplayName("複数日程で生成すると全日程と会場が設定される")
    @Test
    void testCreateWithMultipleDates() {
        final BusinessDate date1 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final BusinessDate date2 = BusinessDate.of(LocalDate.of(2024, 12, 31));
        final List<EventDateAndSpace> dateAndSpaces = List
                .of(EventDateAndSpace.of(date1, "東ホ-01a"), EventDateAndSpace.of(date2, "東ホ-01b"));

        final ConfirmedEvent event = ConfirmedEvent.of("コミックマーケット104", dateAndSpaces, "東京ビッグサイト");

        assertThat(event.name().value()).isEqualTo("コミックマーケット104");
        assertThat(event.dateAndSpaces()).hasSize(2);
        assertThat(event.place()).isEqualTo("東京ビッグサイト");
    }

    @DisplayName("申込イベントから確定イベントを生成すると名前を引き継ぎ日程と会場が設定される")
    @Test
    void testCreateFromTentative() {
        final AppliedEvent applied = AppliedEvent.of("M3-2024春");
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 4, 28));
        final List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "第1展示場A-01"));

        final ConfirmedEvent confirmed = ConfirmedEvent.fromTentative(applied, dateAndSpaces, "東京流通センター");

        assertThat(confirmed.name()).isEqualTo(applied.name());
        assertThat(confirmed.dateAndSpaces()).hasSize(1);
        assertThat(confirmed.place()).isEqualTo("東京流通センター");
    }

    @DisplayName("名前がnullの場合は例外を送出する")
    @Test
    void testCreateWithNullName() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "東ホ-01a"));

        assertThatThrownBy(() -> new ConfirmedEvent(null, dateAndSpaces, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be null");
    }

    @DisplayName("日程が空の場合は例外を送出する")
    @Test
    void testCreateWithEmptyDateAndSpaces() {
        assertThatThrownBy(() -> ConfirmedEvent.of("イベント", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have at least one date and space");
    }

    @DisplayName("日程がnullの場合は例外を送出する")
    @Test
    void testCreateWithNullDateAndSpaces() {
        assertThatThrownBy(() -> new ConfirmedEvent(new EventName("イベント"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have at least one date and space");
    }

    @DisplayName("スペース番号が欠落している場合は例外を送出する")
    @Test
    void testCreateWithMissingSpaceNumber() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, null));

        assertThatThrownBy(() -> new ConfirmedEvent(new EventName("イベント"), dateAndSpaces, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have space number for all dates");
    }

    @DisplayName("スペース番号が空白の場合は例外を送出する")
    @Test
    void testCreateWithBlankSpaceNumber() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date, "  "));

        assertThatThrownBy(() -> new ConfirmedEvent(new EventName("イベント"), dateAndSpaces, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Confirmed event must have space number for all dates");
    }

    @DisplayName("日程リストは変更不可で追加すると例外を送出する")
    @Test
    void testDateAndSpacesIsUnmodifiable() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final ConfirmedEvent event = ConfirmedEvent.of("イベント", date, "A-01");

        assertThatThrownBy(() -> event.dateAndSpaces().add(EventDateAndSpace.of(date, "B-01")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("同一内容のイベント同士は等価と判定される")
    @Test
    void testEquivalentToSame() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final ConfirmedEvent event1 = ConfirmedEvent.of("コミケ", date, "東ホ-01a", "東京ビッグサイト");
        final ConfirmedEvent event2 = ConfirmedEvent.of("コミケ", date, "東ホ-01a", "東京ビッグサイト");

        assertThat(event1.equivalentTo(event2)).isTrue();
    }

    @DisplayName("名前が異なるイベント同士は等価でないと判定される")
    @Test
    void testEquivalentToDifferentName() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final ConfirmedEvent event1 = ConfirmedEvent.of("コミケ103", date, "東ホ-01a");
        final ConfirmedEvent event2 = ConfirmedEvent.of("コミケ104", date, "東ホ-01a");

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @DisplayName("スペースが異なるイベント同士は等価でないと判定される")
    @Test
    void testEquivalentToDifferentSpace() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final ConfirmedEvent event1 = ConfirmedEvent.of("コミケ", date, "東ホ-01a");
        final ConfirmedEvent event2 = ConfirmedEvent.of("コミケ", date, "東ホ-01b");

        assertThat(event1.equivalentTo(event2)).isFalse();
    }

    @DisplayName("nullとの比較は等価でないと判定される")
    @Test
    void testEquivalentToNull() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final ConfirmedEvent event = ConfirmedEvent.of("イベント", date, "A-01");

        assertThat(event.equivalentTo(null)).isFalse();
    }

    @DisplayName("型が異なるイベントとの比較は等価でないと判定される")
    @Test
    void testEquivalentToDifferentType() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 5, 5));
        final ConfirmedEvent confirmed = ConfirmedEvent.of("M3-2024春", date, "A-01");
        final AppliedEvent applied = AppliedEvent.of("M3-2024春", date);

        assertThat(confirmed.equivalentTo(applied)).isFalse();
    }
}
