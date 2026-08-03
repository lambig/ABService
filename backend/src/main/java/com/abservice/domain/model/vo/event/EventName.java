package com.abservice.domain.model.vo.event;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

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
 * <p>
 * 生成は2系統です。信頼できる内部生成にはコンパクトコンストラクタ（不正時は例外）を、外部入力からの 生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            イベント名
 */
public record EventName(String value) implements ValueObject<EventName> {
    /** イベント名の最大長 */
    private static final int MAX_LENGTH = 255;

    /**
     * コンストラクタ
     *
     * @param value
     *            イベント名
     * @throws IllegalArgumentException
     *             名称がnullまたは空白の場合、または最大長を超える場合
     */
    public EventName {
        namePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * 外部入力（文字列）からイベント名を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成にはコンパクトコンストラクタを使用してください。
     * </p>
     *
     * @param value
     *            イベント名を表す文字列
     * @return 成功時は {@code EventName}、失敗時はエラー
     */
    public static Result<EventName> fromInput(@Nullable String value) {
        return namePolicy().verify(value, EventName::new);
    }

    private static Policy<String> namePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "value",
                                "Event name cannot be blank",
                                "EVENT_NAME_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult("value", "Event name must be " + MAX_LENGTH + " characters or less",
                                "EVENT_NAME_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(EventName other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
