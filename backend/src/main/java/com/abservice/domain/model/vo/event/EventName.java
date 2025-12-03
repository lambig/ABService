package com.abservice.domain.model.vo.event;

import com.abservice.domain.model.vo.ValueObject;

import java.util.Optional;

/**
 * イベント名の値オブジェクト
 *
 * <p>
 * イベントの名称を表す値オブジェクトです。 例: "コミックマーケット103", "M3-2024春"
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * @param value
 *            イベント名
 */
public record EventName(String value) implements ValueObject<EventName> {
    /**
     * コンストラクタ
     *
     * @param value
     *            イベント名
     * @throws IllegalArgumentException
     *             名称がnullまたは空白の場合、または最大長を超える場合
     */
    public EventName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Event name must be 255 characters or less");
        }
    }

    @Override
    public boolean equivalentTo(EventName other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
