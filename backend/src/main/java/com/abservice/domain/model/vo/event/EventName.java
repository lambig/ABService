package com.abservice.domain.model.vo.event;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.function.Function;

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
        Policy.all(
                Policy.of(StringUtils::isNotBlank,
                        () -> new ErrorResult("value", "Event name cannot be blank", "EVENT_NAME_REQUIRED")),
                Policy.of((String v) -> StringUtils.length(v) <= 255,
                        () -> new ErrorResult("value", "Event name must be 255 characters or less",
                                "EVENT_NAME_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    @Override
    public boolean equivalentTo(EventName other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
