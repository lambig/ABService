package com.abservice.domain.model.vo.common;

import java.util.Collections;
import java.util.List;

import com.abservice.domain.model.vo.ValueObject;
import com.abservice.domain.model.vo.event.EventName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * イベント頒布情報 Value Object
 *
 * <p>
 * コミケ、M3、ライブなどのイベントでアルバムがリリース（頒布）された情報を表すValue Objectです。
 * イベント名、開催日・スペース番号の組み合わせ（複数日参加対応）、会場、補足情報を含みます。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class EventReleasedAt implements ValueObject<EventReleasedAt> {
    private final EventName name;
    private final List<EventDateAndSpace> dateAndSpaces;
    private final String place;
    private final String note;

    @Override
    public boolean equivalentTo(EventReleasedAt other) {
        if (other == null) {
            return false;
        }
        return this.name.equivalentTo(other.name) && java.util.Objects.equals(this.dateAndSpaces, other.dateAndSpaces)
                && java.util.Objects.equals(this.place, other.place) && java.util.Objects.equals(this.note, other.note);
    }

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param dateAndSpaces
     *            開催日・スペース番号の組み合わせリスト（nullable）
     * @param place
     *            会場（nullable）
     * @param note
     *            補足情報（nullable）
     */
    public EventReleasedAt(EventName name, List<EventDateAndSpace> dateAndSpaces, String place, String note) {
        if (name == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
        this.name = name;
        this.dateAndSpaces = dateAndSpaces != null
                ? Collections.unmodifiableList(dateAndSpaces)
                : Collections.emptyList();
        this.place = place;
        this.note = note;
    }

    /**
     * イベント名のみで生成
     *
     * @param name
     *            イベント名
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(String name) {
        return new EventReleasedAt(new EventName(name), null, null, null);
    }

    /**
     * イベント名と開催日で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(String name, BusinessDate date) {
        return new EventReleasedAt(new EventName(name), date != null ? List.of(EventDateAndSpace.of(date)) : null, null,
                null);
    }

    /**
     * イベント名、開催日、スペース番号で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @param spaceNumber
     *            スペース番号
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(String name, BusinessDate date, String spaceNumber) {
        return new EventReleasedAt(new EventName(name),
                date != null ? List.of(EventDateAndSpace.of(date, spaceNumber)) : null, null, null);
    }

    /**
     * 全ての情報を指定して生成（複数日程対応）
     *
     * @param name
     *            イベント名
     * @param dateAndSpaces
     *            開催日・スペース番号の組み合わせリスト
     * @param place
     *            会場
     * @param note
     *            補足情報
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(String name, List<EventDateAndSpace> dateAndSpaces, String place, String note) {
        return new EventReleasedAt(new EventName(name), dateAndSpaces, place, note);
    }

    /**
     * 単一の日付・スペース情報で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @param place
     *            会場
     * @param spaceNumber
     *            スペース番号
     * @param note
     *            補足情報
     * @return EventReleasedAt
     */
    public static EventReleasedAt of(String name, BusinessDate date, String place, String spaceNumber, String note) {
        return new EventReleasedAt(new EventName(name),
                date != null ? List.of(EventDateAndSpace.of(date, spaceNumber)) : null, place, note);
    }
}
