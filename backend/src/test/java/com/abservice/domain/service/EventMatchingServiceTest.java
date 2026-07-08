package com.abservice.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import com.abservice.domain.model.vo.common.EventReleasedAt;
import com.abservice.domain.model.vo.event.AppliedEvent;
import com.abservice.domain.model.vo.event.ApplyingEvent;
import com.abservice.domain.model.vo.event.ConfirmedEvent;
import com.abservice.domain.model.vo.event.ConsideringEvent;
import com.abservice.domain.model.vo.event.SelectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventMatchingServiceドメインサービス")
class EventMatchingServiceTest {

    private EventMatchingService service;

    @BeforeEach
    void setUp() {
        service = new EventMatchingService();
    }

    @DisplayName("暫定イベントはイベント名のみで同一と判定される")
    @Test
    void testIsSameEventTentativeEventMatchesByNameOnly() {
        AppliedEvent applied = AppliedEvent.of("コミックマーケット104");
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        EventReleasedAt released = EventReleasedAt.of("コミックマーケット104", date, "東ホ-01a");

        assertThat(service.isSameEvent(applied, released)).isTrue();
    }

    @DisplayName("暫定イベントはイベント名が異なると非同一と判定される")
    @Test
    void testIsSameEventTentativeEventWithDifferentName() {
        ConsideringEvent considering = ConsideringEvent.of("コミックマーケット103");
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        EventReleasedAt released = EventReleasedAt.of("コミックマーケット104", date, "東ホ-01a");

        assertThat(service.isSameEvent(considering, released)).isFalse();
    }

    @DisplayName("確定イベントは名前・日付・スペースが完全一致すると同一と判定される")
    @Test
    void testIsSameEventConfirmedEventMatchesExactly() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent confirmed = ConfirmedEvent.of("コミックマーケット104", date, "東ホ-01a");
        EventReleasedAt released = EventReleasedAt.of("コミックマーケット104", date, "東ホ-01a");

        assertThat(service.isSameEvent(confirmed, released)).isTrue();
    }

    @DisplayName("確定イベントはスペースが異なると非同一と判定される")
    @Test
    void testIsSameEventConfirmedEventDifferentSpace() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent confirmed = ConfirmedEvent.of("コミックマーケット104", date, "東ホ-01a");
        EventReleasedAt released = EventReleasedAt.of("コミックマーケット104", date, "東ホ-01b");

        assertThat(service.isSameEvent(confirmed, released)).isFalse();
    }

    @DisplayName("確定イベントは日付が異なると非同一と判定される")
    @Test
    void testIsSameEventConfirmedEventDifferentDate() {
        ConfirmedEvent confirmed = ConfirmedEvent.of("コミケ", BusinessDate.of(LocalDate.of(2024, 12, 30)), "東ホ-01a");
        EventReleasedAt released = EventReleasedAt.of("コミケ", BusinessDate.of(LocalDate.of(2024, 12, 31)), "東ホ-01a");

        assertThat(service.isSameEvent(confirmed, released)).isFalse();
    }

    @DisplayName("確定イベントは複数日程が一致すると同一と判定される")
    @Test
    void testIsSameEventConfirmedEventMultipleDates() {
        BusinessDate date1 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        BusinessDate date2 = BusinessDate.of(LocalDate.of(2024, 12, 31));
        List<EventDateAndSpace> dateAndSpaces = List.of(EventDateAndSpace.of(date1, "東ホ-01a"),
                EventDateAndSpace.of(date2, "東ホ-01b"));

        ConfirmedEvent confirmed = ConfirmedEvent.of("コミケ", dateAndSpaces, "東京ビッグサイト");
        EventReleasedAt released = EventReleasedAt.of("コミケ", dateAndSpaces, "東京ビッグサイト", null);

        assertThat(service.isSameEvent(confirmed, released)).isTrue();
    }

    @DisplayName("参加予定イベントがnullなら非同一と判定される")
    @Test
    void testIsSameEventNullToParticipate() {
        EventReleasedAt released = EventReleasedAt.of("コミケ", BusinessDate.of(LocalDate.of(2024, 12, 30)), "東ホ-01a");

        assertThat(service.isSameEvent(null, released)).isFalse();
    }

    @DisplayName("頒布実績がnullなら非同一と判定される")
    @Test
    void testIsSameEventNullReleasedAt() {
        AppliedEvent applied = AppliedEvent.of("コミケ");

        assertThat(service.isSameEvent(applied, null)).isFalse();
    }

    @DisplayName("暫定イベントは日付未定でも名前一致で同一と判定される")
    @Test
    void testMatchesEventNameAndDateTentativeWithoutDate() {
        ConsideringEvent considering = ConsideringEvent.of("M3-2024春");
        EventReleasedAt released = EventReleasedAt.of("M3-2024春", BusinessDate.of(LocalDate.of(2024, 4, 28)), "A-01");

        assertThat(service.matchesEventNameAndDate(considering, released)).isTrue();
    }

    @DisplayName("暫定イベントは名前と日付が一致すると同一と判定される")
    @Test
    void testMatchesEventNameAndDateTentativeWithMatchingDate() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 4, 28));
        AppliedEvent applied = AppliedEvent.of("M3-2024春", date);
        EventReleasedAt released = EventReleasedAt.of("M3-2024春", date, "A-01");

        assertThat(service.matchesEventNameAndDate(applied, released)).isTrue();
    }

    @DisplayName("暫定イベントは日付が異なると非同一と判定される")
    @Test
    void testMatchesEventNameAndDateTentativeWithDifferentDate() {
        ApplyingEvent applying = ApplyingEvent.of("M3-2024春", BusinessDate.of(LocalDate.of(2024, 4, 28)));
        EventReleasedAt released = EventReleasedAt.of("M3-2024春", BusinessDate.of(LocalDate.of(2024, 4, 29)), "A-01");

        assertThat(service.matchesEventNameAndDate(applying, released)).isFalse();
    }

    @DisplayName("確定イベントは名前と日付が一致すればスペースが違っても同一と判定される")
    @Test
    void testMatchesEventNameAndDateConfirmedWithSameDate() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ConfirmedEvent confirmed = ConfirmedEvent.of("コミケ", date, "東ホ-01a");
        EventReleasedAt released = EventReleasedAt.of("コミケ", date, "東ホ-01b"); // 違うスペース

        assertThat(service.matchesEventNameAndDate(confirmed, released)).isTrue();
    }

    @DisplayName("確定イベントは日付が異なると非同一と判定される")
    @Test
    void testMatchesEventNameAndDateConfirmedWithDifferentDate() {
        ConfirmedEvent confirmed = ConfirmedEvent.of("コミケ", BusinessDate.of(LocalDate.of(2024, 12, 30)), "東ホ-01a");
        EventReleasedAt released = EventReleasedAt.of("コミケ", BusinessDate.of(LocalDate.of(2024, 12, 31)), "東ホ-01a");

        assertThat(service.matchesEventNameAndDate(confirmed, released)).isFalse();
    }

    @DisplayName("イベント名が異なると非同一と判定される")
    @Test
    void testMatchesEventNameAndDateDifferentEventName() {
        ConsideringEvent considering = ConsideringEvent.of("コミケ103");
        EventReleasedAt released = EventReleasedAt.of("コミケ104", BusinessDate.of(LocalDate.of(2024, 12, 30)), "東ホ-01a");

        assertThat(service.matchesEventNameAndDate(considering, released)).isFalse();
    }

    @DisplayName("委託参加は参加予定イベントがないため照合できない")
    @Test
    void testBusinessScenarioConsignmentParticipation() {
        // 委託参加のケース：EventToParticipateがないが、EventReleasedAtは存在
        EventReleasedAt consignmentRelease = EventReleasedAt.of("コミケ104", BusinessDate.of(LocalDate.of(2024, 12, 30)),
                "委託スペース");

        // EventToParticipateがないため、照合できない（nullを渡す）
        assertThat(service.isSameEvent(null, consignmentRelease)).isFalse();
    }

    @DisplayName("申込済みから確定への遷移後、確定イベントと頒布実績が一致する")
    @Test
    void testBusinessScenarioTentativeThenConfirmed() {
        // 申込済み → 確定への状態遷移シナリオ
        AppliedEvent applied = AppliedEvent.of("M3-2024春");

        // その後、スペースが確定
        BusinessDate confirmedDate = BusinessDate.of(LocalDate.of(2024, 4, 28));
        ConfirmedEvent confirmed = ConfirmedEvent.of("M3-2024春", confirmedDate, "第1展示場A-01", "東京流通センター");

        // 実際にアルバムを頒布
        EventReleasedAt released = EventReleasedAt.of("M3-2024春", confirmedDate, "東京流通センター", "第1展示場A-01", null);

        // 確定イベントと頒布実績が一致
        assertThat(service.isSameEvent(confirmed, released)).isTrue();
    }

    @DisplayName("検討中→申込中→申込済み→当選の申込フェーズを遷移できる")
    @Test
    void testBusinessScenarioApplicationPhases() {
        // 検討中
        ConsideringEvent considering = ConsideringEvent.of("コミケ105");
        assertThat(considering.isConsidering()).isTrue();

        // 申込中
        ApplyingEvent applying = considering.startApplying();
        assertThat(applying.isApplying()).isTrue();

        // 申込済み
        AppliedEvent applied = applying.completeApplication();
        assertThat(applied.isApplied()).isTrue();

        // 当選（スペース未定）
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        SelectedEvent selected = SelectedEvent.fromApplied(applied, List.of(date), "東京ビッグサイト");
        assertThat(selected.isSelected()).isTrue();
    }
}
