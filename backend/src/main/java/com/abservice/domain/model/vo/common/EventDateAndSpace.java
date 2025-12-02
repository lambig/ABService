package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * イベント日付・スペース番号 Value Object
 *
 * <p>
 * イベントにおける特定の日付とスペース番号の組み合わせを表すValue Objectです。
 * 同一イベントで複数日参加する場合、各日のスペース番号が異なる可能性があるため、 日付とスペース番号を一つの識別子として扱います。
 * </p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class EventDateAndSpace implements ValueObject<EventDateAndSpace> {
    private final BusinessDate date;
    private final String spaceNumber;

    @Override
    public boolean equivalentTo(EventDateAndSpace other) {
        if (other == null) {
            return false;
        }
        return java.util.Objects.equals(this.date, other.date)
                && java.util.Objects.equals(this.spaceNumber, other.spaceNumber);
    }

    /**
     * コンストラクタ
     *
     * @param date
     *            イベント開催日（必須）
     * @param spaceNumber
     *            スペース番号（nullable、例：東A-01）
     */
    public EventDateAndSpace(BusinessDate date, String spaceNumber) {
        if (date == null) {
            throw new IllegalArgumentException("Event date cannot be null");
        }
        this.date = date;
        this.spaceNumber = spaceNumber;
    }

    /**
     * 日付のみで生成
     *
     * @param date
     *            イベント開催日
     * @return EventDateAndSpace
     */
    public static EventDateAndSpace of(BusinessDate date) {
        return new EventDateAndSpace(date, null);
    }

    /**
     * 日付とスペース番号で生成
     *
     * @param date
     *            イベント開催日
     * @param spaceNumber
     *            スペース番号
     * @return EventDateAndSpace
     */
    public static EventDateAndSpace of(BusinessDate date, String spaceNumber) {
        return new EventDateAndSpace(date, spaceNumber);
    }
}
