package com.abservice.domain.model.vo.album;

import static io.github.lambig.funcifextension.predicate.Predicates.or;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 再生時間の値オブジェクト
 *
 * <p>
 * トラックの再生時間を表す値オブジェクトです。 ミリ秒単位で保持します。
 * </p>
 * <ul>
 * <li>nullは許可されません</li>
 * <li>負の値は許可されません</li>
 * </ul>
 *
 * @param milliseconds
 *            再生時間（ミリ秒）
 */
public record Duration(Integer milliseconds) implements ValueObject<Duration> {
    /**
     * コンストラクタ
     *
     * @param milliseconds
     *            再生時間（ミリ秒）
     * @throws IllegalArgumentException
     *             ミリ秒がnullまたは負の値の場合
     */
    public Duration {
        Policy.<Integer>all(
                Policy.of(
                        Objects::nonNull,
                        () -> new ErrorResult("milliseconds", "Duration milliseconds cannot be null",
                                "DURATION_REQUIRED")),
                Policy.of(
                        or(Objects::isNull, (Integer v) -> v >= 0),
                        () -> new ErrorResult("milliseconds", "Duration milliseconds cannot be negative",
                                "DURATION_NEGATIVE")))
                .verify(milliseconds, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * 秒単位で時間を取得
     *
     * @return 秒数
     */
    public int toSeconds() {
        return milliseconds / 1000;
    }

    /**
     * MM:SS形式で時間を取得
     *
     * @return MM:SS形式の文字列
     */
    public String toMinutesSeconds() {
        final var totalSeconds = toSeconds();
        return String.format(
                "%d:%02d",
                totalSeconds / 60,
                totalSeconds % 60);
    }

    /**
     * HH:MM:SS形式で時間を取得
     *
     * @return HH:MM:SS形式の文字列
     */
    public String toHoursMinutesSeconds() {
        final var totalSeconds = toSeconds();
        return String.format(
                "%d:%02d:%02d",
                totalSeconds / 3600,
                (totalSeconds % 3600) / 60,
                totalSeconds % 60);
    }

    @Override
    public boolean equivalentTo(Duration other) {
        return Optional.ofNullable(other).filter(o -> this.milliseconds.equals(o.milliseconds)).isPresent();
    }
}
