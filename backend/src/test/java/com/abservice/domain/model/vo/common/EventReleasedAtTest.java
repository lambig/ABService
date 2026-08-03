package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.event.EventName;
import com.abservice.lib.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventReleasedAt（初出情報）の生成・同値判定・等価性のテスト")
class EventReleasedAtTest {

    @Test
    @DisplayName("イベント名のみで生成すると、名前が設定され開催日・スペース・会場・備考は空になる")
    void testCreateWithNameOnly() {
        final EventReleasedAt event = EventReleasedAt.of("コミックマーケット101");

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.date()).isNull();
        assertThat(event.spaceNumber()).isNull();
        assertThat(event.place()).isNull();
        assertThat(event.note()).isNull();
    }

    @Test
    @DisplayName("イベント名と開催日で生成すると、名前と開催日が設定されスペース・会場・備考は空になる")
    void testCreateWithNameAndDate() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2023,
                        12,
                        30));
        final EventReleasedAt event = EventReleasedAt.of("コミックマーケット101", date);

        assertThat(event.name().value()).isEqualTo("コミックマーケット101");
        assertThat(event.date()).isEqualTo(date);
        assertThat(event.spaceNumber()).isNull();
        assertThat(event.place()).isNull();
        assertThat(event.note()).isNull();
    }

    @Test
    @DisplayName("イベント名・開催日・スペース番号で生成すると、名前・開催日・スペース番号が設定される")
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
        assertThat(event.date()).isEqualTo(date);
        assertThat(event.spaceNumber()).isEqualTo("東ホ-01a");
    }

    @Test
    @DisplayName("名前・開催日・会場・スペース番号・備考の全情報で生成すると、すべての項目が設定される")
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
        assertThat(event.date()).isEqualTo(date);
        assertThat(event.spaceNumber()).isEqualTo("第1展示場A-01a");
        assertThat(event.place()).isEqualTo("東京流通センター");
        assertThat(event.note()).isEqualTo("新譜あります");
    }

    @Test
    @DisplayName("全項目を明示的にnullで生成すると、名前以外が空になる")
    void testCreateWithNullOptionalFields() {
        final EventName name = new EventName("テストイベント");

        final EventReleasedAt event = EventReleasedAt.of(
                name.value(),
                null,
                null,
                null,
                null);

        assertThat(event.name()).isEqualTo(name);
        assertThat(event.date()).isNull();
        assertThat(event.spaceNumber()).isNull();
        assertThat(event.place()).isNull();
        assertThat(event.note()).isNull();
    }

    @Test
    @DisplayName("イベント名にnullを渡して生成すると、IllegalArgumentExceptionがスローされる")
    void testCreateWithNullName() {
        assertThatThrownBy(
                () -> EventReleasedAt.of(
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Event name cannot be blank");
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

    @Test
    @DisplayName("fromInputは正常な入力で成功する")
    void testFromInputSucceeds() {
        final BusinessDate date = BusinessDate.of(
                LocalDate.of(
                        2026,
                        1,
                        1));
        final Result<EventReleasedAt> result = EventReleasedAt.fromInput(
                "コミケ104",
                date,
                "東京ビッグサイト",
                "東ホ-01a",
                "備考");

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.resolve().name().value()).isEqualTo("コミケ104");
        assertThat(result.resolve().date()).isEqualTo(date);
    }

    @Test
    @DisplayName("fromInputはイベント名が未指定なら失敗する")
    void testFromInputFailsWithBlankName() {
        final Result<EventReleasedAt> result = EventReleasedAt.fromInput(
                "   ",
                null,
                null,
                null,
                null);

        assertThat(result).isInstanceOf(Result.Failure.class);
    }
}
