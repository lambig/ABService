package com.abservice.domain.model.vo.event;

import java.util.Collections;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;

/**
 * 不参加確定イベント Value Object
 *
 * <p>
 * イベントへの参加が不可能になった状態を表します。 全日落選、キャンセル、イベント中止などのケースで使用します。
 * </p>
 *
 * <p>
 * 重要：部分当選・部分落選の扱い
 * </p>
 * <ul>
 * <li>1日でも当選していれば、全体としてSelectedEventとして扱う</li>
 * <li>DeclinedEventは全日落選の場合のみ使用</li>
 * <li>部分落選の日程はSelectedEvent.declinedDatesに記録</li>
 * </ul>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>抽選で全日落選</li>
 * <li>ユーザーによる参加キャンセル</li>
 * <li>イベント主催者による中止</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param declinedDates
 *            不参加確定した日程リスト（必須、1つ以上）
 * @param place
 *            会場（nullable）
 * @param reason
 *            不参加理由（必須）
 */
public record DeclinedEvent(EventName name, List<BusinessDate> declinedDates, String place,
        DeclineReason reason) implements EventToParticipate {

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param declinedDates
     *            不参加確定した日程リスト（必須、1つ以上）
     * @param place
     *            会場（nullable）
     * @param reason
     *            不参加理由（必須）
     * @throws IllegalArgumentException
     *             イベント名がnull、declinedDatesが空、またはreasonがnullの場合
     */
    public DeclinedEvent {
        validateName(name);
        validateDeclinedDates(declinedDates);
        validateReason(reason);
        declinedDates = Collections.unmodifiableList(declinedDates);
    }

    private static void validateName(EventName name) {
        if (name == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
    }

    private static void validateDeclinedDates(List<BusinessDate> dates) {
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("Declined event must have at least one declined date");
        }
    }

    private static void validateReason(DeclineReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Decline reason cannot be null");
        }
    }

    /**
     * 単一日程で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            不参加日
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent of(String name, BusinessDate date, DeclineReason reason) {
        return new DeclinedEvent(new EventName(name), List.of(date), null, reason);
    }

    /**
     * 単一日程で会場情報付きで生成
     *
     * @param name
     *            イベント名
     * @param date
     *            不参加日
     * @param place
     *            会場
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent of(String name, BusinessDate date, String place, DeclineReason reason) {
        return new DeclinedEvent(new EventName(name), List.of(date), place, reason);
    }

    /**
     * 複数日程で生成
     *
     * @param name
     *            イベント名
     * @param declinedDates
     *            不参加日程リスト
     * @param place
     *            会場
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent of(String name, List<BusinessDate> declinedDates, String place, DeclineReason reason) {
        return new DeclinedEvent(new EventName(name), declinedDates, place, reason);
    }

    /**
     * TentativeEventから不参加イベントに変換
     *
     * @param tentative
     *            未確定イベント
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent fromTentative(TentativeEvent tentative, DeclineReason reason) {
        var dates = tentative.tentativeDates();
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("Cannot decline event without dates");
        }
        return new DeclinedEvent(tentative.name(), dates, null, reason);
    }

    /**
     * AppliedEventから不参加イベントに変換
     *
     * @param applied
     *            申込済みイベント
     * @param reason
     *            不参加理由
     * @return DeclinedEvent
     */
    public static DeclinedEvent fromApplied(AppliedEvent applied, DeclineReason reason) {
        var dates = applied.tentativeDates();
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("Cannot decline event without dates");
        }
        return new DeclinedEvent(applied.name(), dates, null, reason);
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        return java.util.Optional.ofNullable(other).filter(o -> o instanceof DeclinedEvent).map(o -> (DeclinedEvent) o)
                .map(declined -> this.name.equivalentTo(declined.name)
                        && this.declinedDates.equals(declined.declinedDates)
                        && java.util.Objects.equals(this.place, declined.place) && this.reason == declined.reason)
                .orElse(false);
    }
}
