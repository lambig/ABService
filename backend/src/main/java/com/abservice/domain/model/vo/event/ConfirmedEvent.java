package com.abservice.domain.model.vo.event;

import java.util.Collections;
import java.util.List;

import com.abservice.domain.model.vo.common.BusinessDate;
import com.abservice.domain.model.vo.common.EventDateAndSpace;
import org.apache.commons.lang3.StringUtils;

/**
 * 確定イベント Value Object
 *
 * <p>
 * 参加が確定したイベント情報を表します。 日付とスペース番号が決定した状態です（複数日参加対応）。
 * </p>
 *
 * <p>
 * 使用例：
 * </p>
 * <ul>
 * <li>「コミックマーケット103」2023/12/30 東ホ-01a、2023/12/31 東ホ-01b</li>
 * <li>「M3-2024秋」2024/10/27 第1展示場A-01</li>
 * </ul>
 *
 * @param name
 *            イベント名（必須）
 * @param dateAndSpaces
 *            開催日・スペース番号の組み合わせリスト（必須、1つ以上）
 * @param place
 *            会場（nullable、例：東京ビッグサイト）
 */
public record ConfirmedEvent(EventName name, List<EventDateAndSpace> dateAndSpaces,
        String place) implements EventToParticipate {

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param dateAndSpaces
     *            開催日・スペース番号の組み合わせリスト（必須、1つ以上）
     * @param place
     *            会場（nullable）
     * @throws IllegalArgumentException
     *             イベント名がnull、またはdateAndSpacesが空の場合
     */
    public ConfirmedEvent {
        if (name == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
        if (dateAndSpaces == null || dateAndSpaces.isEmpty()) {
            throw new IllegalArgumentException("Confirmed event must have at least one date and space");
        }
        validateAllSpaceNumbers(dateAndSpaces);
        dateAndSpaces = Collections.unmodifiableList(dateAndSpaces);
    }

    private static void validateAllSpaceNumbers(List<EventDateAndSpace> dateAndSpaces) {
        if (dateAndSpaces.stream().anyMatch(ds -> StringUtils.isBlank(ds.spaceNumber()))) {
            throw new IllegalArgumentException("Confirmed event must have space number for all dates");
        }
    }

    /**
     * 単一日程で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @param spaceNumber
     *            スペース番号
     * @return ConfirmedEvent
     */
    public static ConfirmedEvent of(String name, BusinessDate date, String spaceNumber) {
        return new ConfirmedEvent(new EventName(name), List.of(EventDateAndSpace.of(date, spaceNumber)), null);
    }

    /**
     * 単一日程で会場情報付きで生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @param spaceNumber
     *            スペース番号
     * @param place
     *            会場
     * @return ConfirmedEvent
     */
    public static ConfirmedEvent of(String name, BusinessDate date, String spaceNumber, String place) {
        return new ConfirmedEvent(new EventName(name), List.of(EventDateAndSpace.of(date, spaceNumber)), place);
    }

    /**
     * 複数日程で生成
     *
     * @param name
     *            イベント名
     * @param dateAndSpaces
     *            開催日・スペース番号の組み合わせリスト
     * @param place
     *            会場
     * @return ConfirmedEvent
     */
    public static ConfirmedEvent of(String name, List<EventDateAndSpace> dateAndSpaces, String place) {
        return new ConfirmedEvent(new EventName(name), dateAndSpaces, place);
    }

    /**
     * TentativeEventから確定イベントに変換
     *
     * @param tentative
     *            未確定イベント
     * @param dateAndSpaces
     *            確定した開催日・スペース番号リスト
     * @param place
     *            会場
     * @return ConfirmedEvent
     */
    public static ConfirmedEvent fromTentative(TentativeEvent tentative, List<EventDateAndSpace> dateAndSpaces,
            String place) {
        return new ConfirmedEvent(tentative.name(), dateAndSpaces, place);
    }

    /**
     * SelectedEventから確定イベントに変換（スペース番号追加）
     *
     * @param selected
     *            当選イベント（スペース未定）
     * @param dateAndSpaces
     *            確定した開催日・スペース番号リスト
     * @return ConfirmedEvent
     */
    public static ConfirmedEvent fromSelected(SelectedEvent selected, List<EventDateAndSpace> dateAndSpaces) {
        return new ConfirmedEvent(selected.name(), dateAndSpaces, selected.place());
    }

    @Override
    public boolean equivalentTo(EventToParticipate other) {
        return java.util.Optional.ofNullable(other).filter(o -> o instanceof ConfirmedEvent)
                .map(o -> (ConfirmedEvent) o)
                .map(confirmed -> this.name.equivalentTo(confirmed.name)
                        && this.dateAndSpaces.equals(confirmed.dateAndSpaces)
                        && java.util.Objects.equals(this.place, confirmed.place))
                .orElse(false);
    }
}
