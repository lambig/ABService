package com.abservice.domain.model.vo.event;

import com.abservice.domain.model.vo.ValueObject;

/**
 * 参加予定イベント Value Object
 *
 * <p>
 * 将来参加する予定のイベント情報を表すValue Objectです。 アルバム記事との紐付きはありません（EventReleasedAtとは独立）。
 * </p>
 *
 * <p>
 * 状態遷移：
 * </p>
 *
 * <pre>
 * TentativeEvent（未確定）
 *     ↓
 * SelectedEvent（当選・スペース未定）
 *     ↓
 * ConfirmedEvent（確定・スペースあり）
 *
 * 各状態から → DeclinedEvent（不参加確定）への遷移可能
 * </pre>
 *
 * <p>
 * 状態の型：
 * </p>
 * <ul>
 * <li>{@link TentativeEvent}: 申込検討中・申込中（スペース未定）</li>
 * <li>{@link SelectedEvent}: 当選（スペース番号未確定）</li>
 * <li>{@link ConfirmedEvent}: 当選確定（スペース番号あり）</li>
 * <li>{@link DeclinedEvent}: 不参加確定（落選・キャンセル・中止）</li>
 * </ul>
 *
 * <p>
 * 業務知識：
 * </p>
 * <ul>
 * <li>EventToParticipateの絶対数はEventReleasedAtより多い</li>
 * <li>EventReleasedAtを完全には包含しない（委託参加のケースではEventToParticipateが存在しない）</li>
 * <li>複数日開催イベントで部分当選・部分落選が発生しうる</li>
 * </ul>
 */
public sealed interface EventToParticipate extends ValueObject<EventToParticipate>
        permits TentativeEvent, SelectedEvent, ConfirmedEvent, DeclinedEvent {

    /**
     * イベント名を取得
     *
     * @return イベント名
     */
    EventName name();

    /**
     * 未確定イベントかどうかを判定
     *
     * @return 未確定イベントの場合true
     */
    default boolean isTentative() {
        return this instanceof TentativeEvent;
    }

    /**
     * 当選イベント（スペース未定）かどうかを判定
     *
     * @return 当選イベントの場合true
     */
    default boolean isSelected() {
        return this instanceof SelectedEvent;
    }

    /**
     * 確定イベント（スペースあり）かどうかを判定
     *
     * @return 確定イベントの場合true
     */
    default boolean isConfirmed() {
        return this instanceof ConfirmedEvent;
    }

    /**
     * 不参加確定イベントかどうかを判定
     *
     * @return 不参加確定の場合true
     */
    default boolean isDeclined() {
        return this instanceof DeclinedEvent;
    }
}
