package com.abservice.domain.model.vo.event;

import java.util.Collections;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;

/**
 * 検討中イベント Value Object
 *
 * <p>
 * イベント参加を検討中の状態を表します。 まだ申込を開始していないため、先方に迷惑をかけないようイベント名を匿名化する選択肢があります。
 * </p>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>「コミックマーケット105」への参加を検討中（2日間開催）</li>
 * <li>「M3-2025春」の申込を検討しているが未確定</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param tentativeDates
 *            暫定開催日リスト（空可、未定の場合は空リスト）
 */
public record ConsideringEvent(EventName name, List<BusinessDate> tentativeDates) implements TentativeEvent {

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param tentativeDates
     *            暫定開催日リスト（空可）
     * @throws IllegalArgumentException
     *             イベント名がnullの場合
     */
    public ConsideringEvent {
        if (name == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
        if (tentativeDates == null) {
            tentativeDates = List.of();
        }
        tentativeDates = Collections.unmodifiableList(tentativeDates);
    }

    /**
     * イベント名のみで生成（日程未定）
     *
     * @param name
     *            イベント名
     * @return ConsideringEvent
     */
    public static ConsideringEvent of(String name) {
        return new ConsideringEvent(new EventName(name), List.of());
    }

    /**
     * イベント名と単一日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDate
     *            暫定開催日
     * @return ConsideringEvent
     */
    public static ConsideringEvent of(String name, BusinessDate tentativeDate) {
        return new ConsideringEvent(new EventName(name), List.of(tentativeDate));
    }

    /**
     * イベント名と複数日程で生成
     *
     * @param name
     *            イベント名
     * @param tentativeDates
     *            暫定開催日リスト
     * @return ConsideringEvent
     */
    public static ConsideringEvent of(String name, List<BusinessDate> tentativeDates) {
        return new ConsideringEvent(new EventName(name), tentativeDates);
    }

    /**
     * 申込中へ状態遷移
     *
     * @return ApplyingEvent
     */
    public ApplyingEvent startApplying() {
        return ApplyingEvent.from(this);
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        if (other == null) {
            return false;
        }
        if (!(other instanceof ConsideringEvent considering)) {
            return false;
        }
        return this.name.equivalentTo(considering.name) && this.tentativeDates.equals(considering.tentativeDates);
    }
}
