package com.abservice.domain.model.vo.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.abservice.domain.model.vo.common.BusinessDate;
import org.junit.jupiter.api.Test;

class TentativeEventTest {

    @Test
    void testConsideringEvent() {
        ConsideringEvent event = ConsideringEvent.of("コミックマーケット105");

        assertThat(event.name().value()).isEqualTo("コミックマーケット105");
        assertThat(event.tentativeDates()).isEmpty();
        assertThat(event.isConsidering()).isTrue();
        assertThat(event.isApplying()).isFalse();
        assertThat(event.isApplied()).isFalse();
        assertThat(event.isTentative()).isTrue();
    }

    @Test
    void testApplyingEvent() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        ApplyingEvent event = ApplyingEvent.of("コミックマーケット105", date);

        assertThat(event.name().value()).isEqualTo("コミックマーケット105");
        assertThat(event.tentativeDates()).containsExactly(date);
        assertThat(event.isConsidering()).isFalse();
        assertThat(event.isApplying()).isTrue();
        assertThat(event.isApplied()).isFalse();
        assertThat(event.isTentative()).isTrue();
    }

    @Test
    void testAppliedEvent() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2024, 12, 30));
        AppliedEvent event = AppliedEvent.of("コミックマーケット105", date);

        assertThat(event.name().value()).isEqualTo("コミックマーケット105");
        assertThat(event.tentativeDates()).containsExactly(date);
        assertThat(event.isConsidering()).isFalse();
        assertThat(event.isApplying()).isFalse();
        assertThat(event.isApplied()).isTrue();
        assertThat(event.isTentative()).isTrue();
    }

    @Test
    void testStateTransitionConsideringToApplying() {
        ConsideringEvent considering = ConsideringEvent.of("M3-2025春");
        ApplyingEvent applying = considering.startApplying();

        assertThat(applying.name()).isEqualTo(considering.name());
        assertThat(applying.tentativeDates()).isEqualTo(considering.tentativeDates());
    }

    @Test
    void testStateTransitionApplyingToApplied() {
        BusinessDate date = BusinessDate.of(LocalDate.of(2025, 4, 27));
        ApplyingEvent applying = ApplyingEvent.of("M3-2025春", date);
        AppliedEvent applied = applying.completeApplication();

        assertThat(applied.name()).isEqualTo(applying.name());
        assertThat(applied.tentativeDates()).isEqualTo(applying.tentativeDates());
    }

    @Test
    void testStateTransitionConsideringToAppliedDirectly() {
        ConsideringEvent considering = ConsideringEvent.of("地元フェス");
        AppliedEvent applied = AppliedEvent.from(considering);

        assertThat(applied.name()).isEqualTo(considering.name());
        assertThat(applied.tentativeDates()).isEqualTo(considering.tentativeDates());
    }

    @Test
    void testStateTransitionFullFlow() {
        // 検討中
        ConsideringEvent considering = ConsideringEvent.of("コミケ105");

        // 申込中
        ApplyingEvent applying = considering.startApplying();
        assertThat(applying.isApplying()).isTrue();

        // 申込済み
        AppliedEvent applied = applying.completeApplication();
        assertThat(applied.isApplied()).isTrue();
    }

    @Test
    void testMultipleDates() {
        BusinessDate day1 = BusinessDate.of(LocalDate.of(2024, 12, 30));
        BusinessDate day2 = BusinessDate.of(LocalDate.of(2024, 12, 31));

        AppliedEvent applied = AppliedEvent.of("コミケ105", java.util.List.of(day1, day2));

        assertThat(applied.tentativeDates()).hasSize(2);
        assertThat(applied.tentativeDates()).containsExactly(day1, day2);
    }
}
