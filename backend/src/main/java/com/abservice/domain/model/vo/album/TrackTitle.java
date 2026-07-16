package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.function.Function;

/**
 * トラックタイトルの値オブジェクト
 *
 * <p>
 * トラックのタイトルを表す値オブジェクトです。 以下の制約を持ちます：
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * @param value
 *            トラックタイトル
 */
public record TrackTitle(String value) implements ValueObject<TrackTitle> {
    /**
     * コンストラクタ
     *
     * @param value
     *            トラックタイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public TrackTitle {
        Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "trackTitle",
                                "Track title cannot be blank",
                                "TRACK_TITLE_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= 255,
                        () -> new ErrorResult("trackTitle", "Track title must be 255 characters or less",
                                "TRACK_TITLE_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            トラックタイトル
     * @return TrackTitleインスタンス
     */
    public static TrackTitle of(String value) {
        return new TrackTitle(value);
    }

    @Override
    public boolean equivalentTo(TrackTitle other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
