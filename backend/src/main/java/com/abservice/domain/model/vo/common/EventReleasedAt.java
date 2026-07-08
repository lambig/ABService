package com.abservice.domain.model.vo.common;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.domain.model.vo.event.EventName;
import com.abservice.lib.ErrorResult;
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
public final class EventReleasedAt implements ValueObject<EventReleasedAt> {
    private final EventName name;
    private final List<EventDateAndSpace> dateAndSpaces;
    private final String place;
    private final String note;

    @Override
    public boolean equivalentTo(EventReleasedAt other) {
        return Optional.ofNullable(other)
                .map(o -> this.name.equivalentTo(o.name) && Objects.equals(this.dateAndSpaces, o.dateAndSpaces)
                        && Objects.equals(this.place, o.place) && Objects.equals(this.note, o.note))
                .orElse(false);
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
    private EventReleasedAt(EventName name, List<EventDateAndSpace> dateAndSpaces, String place, String note) {
        Policy.<EventName>of(Objects::nonNull,
                () -> new ErrorResult("name", "Event name cannot be null", "NAME_REQUIRED"))
                .verify(name, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        this.name = name;
        this.dateAndSpaces = Optional.ofNullable(dateAndSpaces)
                .<List<EventDateAndSpace>>map(Collections::unmodifiableList).orElse(Collections.emptyList());
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
        return new EventReleasedAt(new EventName(name),
                Optional.ofNullable(date).map(d -> List.of(EventDateAndSpace.of(d))).orElse(null), null, null);
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
                Optional.ofNullable(date).map(d -> List.of(EventDateAndSpace.of(d, spaceNumber))).orElse(null), null,
                null);
    }

    /**
     * イベント名と年月日で生成
     *
     * @param name
     *            イベント名
     * @param year
     *            年
     * @param month
     *            月
     * @param dayOfMonth
     *            日
     * @return EventReleasedAt
     */
    public static EventReleasedAt atEvent(String name, int year, int month, int dayOfMonth) {
        return of(name, BusinessDate.of(year, month, dayOfMonth));
    }

    /**
     * イベント名、年月日、開催場所で生成
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
                Optional.ofNullable(date).map(d -> List.of(EventDateAndSpace.of(d, spaceNumber))).orElse(null), place,
                note);
    }
}
