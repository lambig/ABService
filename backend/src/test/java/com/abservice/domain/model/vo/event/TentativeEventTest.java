package com.abservice.domain.model.vo.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.abservice.domain.model.vo.common.BusinessDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("暫定イベント（検討中・申込中・申込済み）の状態と遷移")
class TentativeEventTest {

    @DisplayName("検討中イベントは名前を持ち日程が空で検討中かつ暫定状態である")
    @Test
    void testConsideringEvent() {
        final ConsideringEvent event = ConsideringEvent.of("コミックマーケット105");

        assertThat(event.name().value()).isEqualTo("コミックマーケット105");
        assertThat(event.tentativeDates()).isEmpty();
        assertThat(event.isConsidering()).isTrue();
        assertThat(event.isApplying()).isFalse();
        assertThat(event.isApplied()).isFalse();
        assertThat(event.isTentative()).isTrue();
    }

    @DisplayName("申込中イベントは名前と暫定日程を持ち申込中かつ暫定状態である")
    @Test
    void testApplyingEvent() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final ApplyingEvent event = ApplyingEvent.of("コミックマーケット105", date);

        assertThat(event.name().value()).isEqualTo("コミックマーケット105");
        assertThat(event.tentativeDates()).containsExactly(date);
        assertThat(event.isConsidering()).isFalse();
        assertThat(event.isApplying()).isTrue();
        assertThat(event.isApplied()).isFalse();
        assertThat(event.isTentative()).isTrue();
    }

    @DisplayName("申込済みイベントは名前と暫定日程を持ち申込済みかつ暫定状態である")
    @Test
    void testAppliedEvent() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final AppliedEvent event = AppliedEvent.of("コミックマーケット105", date);

        assertThat(event.name().value()).isEqualTo("コミックマーケット105");
        assertThat(event.tentativeDates()).containsExactly(date);
        assertThat(event.isConsidering()).isFalse();
        assertThat(event.isApplying()).isFalse();
        assertThat(event.isApplied()).isTrue();
        assertThat(event.isTentative()).isTrue();
    }

    @DisplayName("検討中から申込中へ遷移しても名前と日程が引き継がれる")
    @Test
    void testStateTransitionConsideringToApplying() {
        final ConsideringEvent considering = ConsideringEvent.of("M3-2025春");
        final ApplyingEvent applying = considering.startApplying();

        assertThat(applying.name()).isEqualTo(considering.name());
        assertThat(applying.tentativeDates()).isEqualTo(considering.tentativeDates());
    }

    @DisplayName("申込中から申込済みへ遷移しても名前と日程が引き継がれる")
    @Test
    void testStateTransitionApplyingToApplied() {
        final BusinessDate date = BusinessDate.of(LocalDate.of(2025, 4, 27));
        final ApplyingEvent applying = ApplyingEvent.of("M3-2025春", date);
        final AppliedEvent applied = applying.completeApplication();

        assertThat(applied.name()).isEqualTo(applying.name());
        assertThat(applied.tentativeDates()).isEqualTo(applying.tentativeDates());
    }

    @DisplayName("検討中から申込済みへ直接遷移しても名前と日程が引き継がれる")
    @Test
    void testStateTransitionConsideringToAppliedDirectly() {
        final ConsideringEvent considering = ConsideringEvent.of("地元フェス");
        final AppliedEvent applied = AppliedEvent.from(considering);

        assertThat(applied.name()).isEqualTo(considering.name());
        assertThat(applied.tentativeDates()).isEqualTo(considering.tentativeDates());
    }

    @DisplayName("検討中から申込中を経て申込済みまで一連の遷移ができる")
    @Test
    void testStateTransitionFullFlow() {
        // 検討中
        final ConsideringEvent considering = ConsideringEvent.of("コミケ105");

        // 申込中
        final ApplyingEvent applying = considering.startApplying();
        assertThat(applying.isApplying()).isTrue();

        // 申込済み
        final AppliedEvent applied = applying.completeApplication();
        assertThat(applied.isApplied()).isTrue();
    }

    @DisplayName("複数の暫定日程を持つ申込済みイベントを生成できる")
    @Test
    void testMultipleDates() {
        final BusinessDate day1 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        final BusinessDate day2 = BusinessDate.of(LocalDate.of(2024, 12, 31));

        final AppliedEvent applied = AppliedEvent.of("コミケ105", java.util.List.of(day1, day2));

        assertThat(applied.tentativeDates()).hasSize(2);
        assertThat(applied.tentativeDates()).containsExactly(day1, day2);
    }
}
