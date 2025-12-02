package com.abservice.domain.model.vo.common;

import java.time.LocalDate;

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
 * イベント名、開催日、会場、スペース番号などを含みます。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class EventReleasedAt implements ValueObject<EventReleasedAt> {
    private final EventName name;
    private final LocalDate date;
    private final String place;
    private final String spaceNumber;
    private final String note;

    @Override
    public boolean equivalentTo(EventReleasedAt other) {
        if (other == null) {
            return false;
        }
        return this.name.equivalentTo(other.name) && java.util.Objects.equals(this.date, other.date)
                && java.util.Objects.equals(this.place, other.place)
                && java.util.Objects.equals(this.spaceNumber, other.spaceNumber)
                && java.util.Objects.equals(this.note, other.note);
    }

    /**
     * コンストラクタ
     *
     * @param name
     *            イベント名（必須）
     * @param date
     *            開催日（nullable）
     * @param place
     *            会場（nullable）
     * @param spaceNumber
     *            スペース番号（nullable、例：東A-01）
     * @param note
     *            補足情報（nullable）
     */
    public EventReleasedAt(EventName name, LocalDate date, String place, String spaceNumber, String note) {
        if (name == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
        this.name = name;
        this.date = date;
        this.place = place;
        this.spaceNumber = spaceNumber;
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
        return new EventReleasedAt(new EventName(name), null, null, null, null);
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
    public static EventReleasedAt of(String name, LocalDate date) {
        return new EventReleasedAt(new EventName(name), date, null, null, null);
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
    public static EventReleasedAt of(String name, LocalDate date, String spaceNumber) {
        return new EventReleasedAt(new EventName(name), date, null, spaceNumber, null);
    }

    /**
     * 全ての情報を指定して生成
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
    public static EventReleasedAt of(String name, LocalDate date, String place, String spaceNumber, String note) {
        return new EventReleasedAt(new EventName(name), date, place, spaceNumber, note);
    }
}
