package com.abservice.domain.model.vo.event;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.abservice.domain.model.vo.common.BusinessDate;

/**
 * 当選イベント（スペース未確定） Value Object
 *
 * <p>
 * 抽選に当選したがスペース番号が未確定の状態を表します。 ローカルイベントなど、当選通知後にスペース番号が後日通知されるケースで使用します。
 * </p>
 *
 * <p>
 * 部分当選・部分落選の扱い：
 * </p>
 * <ul>
 * <li>1日でも当選していれば全体として当選イベント（SelectedEvent）として扱う</li>
 * <li>selectedDatesに当選日、declinedDatesに落選日を記録</li>
 * <li>全日落選の場合のみDeclinedEventとして扱う</li>
 * </ul>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>「〇〇ライブ」出店OK、スペース番号は後日連絡</li>
 * <li>「地域イベント」当選、配置は当日決定</li>
 * <li>「M3-2024春」2日開催のうち1日目のみ当選（2日目は落選）</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param selectedDates
 *            当選した日程リスト（必須、1つ以上）
 * @param declinedDates
 *            落選した日程リスト（空可、部分落選時のみ使用）
 * @param place
 *            会場（nullable）
 */
public record SelectedEvent(EventName name, List<BusinessDate> selectedDates, List<BusinessDate> declinedDates,
        String place) implements EventToParticipate {

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param selectedDates
     *            当選した日程リスト（必須、1つ以上）
     * @param declinedDates
     *            落選した日程リスト（空可）
     * @param place
     *            会場（nullable）
     * @throws IllegalArgumentException
     *             イベント名がnull、またはselectedDatesが空の場合
     */
    public SelectedEvent {
        validateName(name);
        validateSelectedDates(selectedDates);
        declinedDates = normalizeDeclinedDates(declinedDates);
        selectedDates = Collections.unmodifiableList(selectedDates);
        declinedDates = Collections.unmodifiableList(declinedDates);
    }

    private static void validateName(EventName name) {
        if (name == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
    }

    private static void validateSelectedDates(List<BusinessDate> dates) {
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("Selected event must have at least one selected date");
        }
    }

    private static List<BusinessDate> normalizeDeclinedDates(List<BusinessDate> dates) {
        return dates != null ? dates : List.of();
    }

    /**
     * 単一日程で生成（全当選）
     *
     * @param name
     *            イベント名
     * @param date
     *            当選日
     * @return SelectedEvent
     */
    public static SelectedEvent of(String name, BusinessDate date) {
        return new SelectedEvent(new EventName(name), List.of(date), List.of(), null);
    }

    /**
     * 単一日程で会場情報付きで生成（全当選）
     *
     * @param name
     *            イベント名
     * @param date
     *            当選日
     * @param place
     *            会場
     * @return SelectedEvent
     */
    public static SelectedEvent of(String name, BusinessDate date, String place) {
        return new SelectedEvent(new EventName(name), List.of(date), List.of(), place);
    }

    /**
     * 複数日程で生成（全当選）
     *
     * @param name
     *            イベント名
     * @param selectedDates
     *            当選日リスト
     * @param place
     *            会場
     * @return SelectedEvent
     */
    public static SelectedEvent of(String name, List<BusinessDate> selectedDates, String place) {
        return new SelectedEvent(new EventName(name), selectedDates, List.of(), place);
    }

    /**
     * 部分当選で生成
     *
     * @param name
     *            イベント名
     * @param selectedDates
     *            当選日リスト
     * @param declinedDates
     *            落選日リスト
     * @param place
     *            会場
     * @return SelectedEvent
     */
    public static SelectedEvent ofPartial(String name, List<BusinessDate> selectedDates,
            List<BusinessDate> declinedDates, String place) {
        return new SelectedEvent(new EventName(name), selectedDates, declinedDates, place);
    }

    /**
     * TentativeEventから当選イベントに変換（後方互換用）
     *
     * @param tentative
     *            未確定イベント
     * @param selectedDates
     *            当選した日程リスト
     * @param place
     *            会場
     * @return SelectedEvent
     * @deprecated {@link #fromApplied(TentativeEvent, List, String)}を使用してください
     */
    @Deprecated
    public static SelectedEvent fromTentative(TentativeEvent tentative, List<BusinessDate> selectedDates,
            String place) {
        return fromApplied(tentative, selectedDates, place);
    }

    /**
     * 部分当選かどうかを判定
     *
     * @return 落選日が1つ以上ある場合true
     */
    public boolean isPartialSelection() {
        return !declinedDates.isEmpty();
    }

    /**
     * 全当選かどうかを判定
     *
     * @return 落選日がない場合true
     */
    public boolean isFullSelection() {
        return declinedDates.isEmpty();
    }

    /**
     * AppliedEventから当選イベントに変換（全当選）
     *
     * @param applied
     *            申込済みイベント
     * @param selectedDates
     *            当選した日程リスト
     * @param place
     *            会場
     * @return SelectedEvent
     */
    public static SelectedEvent fromApplied(TentativeEvent applied, List<BusinessDate> selectedDates, String place) {
        return new SelectedEvent(applied.name(), selectedDates, List.of(), place);
    }

    /**
     * AppliedEventから部分当選イベントに変換
     *
     * @param applied
     *            申込済みイベント
     * @param selectedDates
     *            当選した日程リスト
     * @param declinedDates
     *            落選した日程リスト
     * @param place
     *            会場
     * @return SelectedEvent
     */
    public static SelectedEvent fromAppliedPartial(TentativeEvent applied, List<BusinessDate> selectedDates,
            List<BusinessDate> declinedDates, String place) {
        return new SelectedEvent(applied.name(), selectedDates, declinedDates, place);
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        return Optional.ofNullable(other).filter(o -> o instanceof SelectedEvent).map(o -> (SelectedEvent) o)
                .map(selected -> this.name.equivalentTo(selected.name)
                        && this.selectedDates.equals(selected.selectedDates)
                        && Objects.equals(this.place, selected.place))
                .orElse(false);
    }
}
