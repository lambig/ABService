package com.abservice.domain.model.vo.common;

import java.time.LocalDate;

import com.abservice.domain.model.vo.ValueObject;
import com.abservice.domain.model.vo.event.EventName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * イベント情報 Value Object
 *
 * <p>
 * コミケ、M3、ライブなどのイベント情報を表すValue Objectです。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class EventInfo implements ValueObject<EventInfo> {
    private final EventName name;
    private final LocalDate date;
    private final String place;
    private final String note;

    @Override
    public boolean equivalentTo(EventInfo other) {
        if (other == null) {
            return false;
        }
        return this.name.equivalentTo(other.name) && java.util.Objects.equals(this.date, other.date)
                && java.util.Objects.equals(this.place, other.place) && java.util.Objects.equals(this.note, other.note);
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
     * @param note
     *            補足情報（nullable）
     */
    public EventInfo(EventName name, LocalDate date, String place, String note) {
        if (name == null) {
            throw new IllegalArgumentException("Event name cannot be null");
        }
        this.name = name;
        this.date = date;
        this.place = place;
        this.note = note;
    }

    /**
     * イベント名のみで生成
     *
     * @param name
     *            イベント名
     * @return EventInfo
     */
    public static EventInfo of(String name) {
        return new EventInfo(new EventName(name), null, null, null);
    }

    /**
     * イベント名と開催日で生成
     *
     * @param name
     *            イベント名
     * @param date
     *            開催日
     * @return EventInfo
     */
    public static EventInfo of(String name, LocalDate date) {
        return new EventInfo(new EventName(name), date, null, null);
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
     * @param note
     *            補足情報
     * @return EventInfo
     */
    public static EventInfo of(String name, LocalDate date, String place, String note) {
        return new EventInfo(new EventName(name), date, place, note);
    }
}
