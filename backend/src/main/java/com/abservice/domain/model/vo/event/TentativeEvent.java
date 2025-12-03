package com.abservice.domain.model.vo.event;

import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;

/**
 * 未確定イベント Value Object（申込段階）
 *
 * <p>
 * 抽選結果が出る前の申込段階を表すインターフェースです。 申込フェーズに応じて3つの具体型に分かれます。
 * </p>
 *
 * <p>
 * 状態遷移：
 * </p>
 *
 * <pre>
 * ConsideringEvent（検討中）
 *     ↓
 * ApplyingEvent（申込中）
 *     ↓
 * AppliedEvent（申込済み）
 *     ↓
 * SelectedEvent / DeclinedEvent（抽選結果）
 * </pre>
 *
 * <p>
 * 具体型：
 * </p>
 * <ul>
 * <li>{@link ConsideringEvent}: 申込検討中（先方に迷惑をかけないため匿名化可能）</li>
 * <li>{@link ApplyingEvent}: 申込中</li>
 * <li>{@link AppliedEvent}: 申込済み（抽選待ち）</li>
 * </ul>
 */
public sealed interface TentativeEvent extends EventToParticipate
        permits ConsideringEvent, ApplyingEvent, AppliedEvent {

    /**
     * 暫定開催日リストを取得
     *
     * @return 暫定開催日リスト（空可、未定の場合は空リスト）
     */
    List<BusinessDate> tentativeDates();

    /**
     * 検討中イベントかどうかを判定
     *
     * @return 検討中の場合true
     */
    default boolean isConsidering() {
        return this instanceof ConsideringEvent;
    }

    /**
     * 申込中イベントかどうかを判定
     *
     * @return 申込中の場合true
     */
    default boolean isApplying() {
        return this instanceof ApplyingEvent;
    }

    /**
     * 申込済みイベントかどうかを判定
     *
     * @return 申込済みの場合true
     */
    default boolean isApplied() {
        return this instanceof AppliedEvent;
    }
}
