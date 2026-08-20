package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

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
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
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
        titlePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            トラックタイトル
     * @return TrackTitleインスタンス
     */
    public static TrackTitle of(String value) {
        return new TrackTitle(value);
    }

    /**
     * 外部入力（文字列）からトラックタイトルを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            トラックタイトルを表す文字列
     * @return 成功時は {@code TrackTitle}、失敗時はエラー
     */
    public static Result<TrackTitle> fromInput(@Nullable String value) {
        return titlePolicy().verify(value, TrackTitle::new);
    }

    private static Policy<String> titlePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "trackTitle",
                                "Track title cannot be blank",
                                "TRACK_TITLE_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= 255,
                        () -> new ErrorResult("trackTitle", "Track title must be 255 characters or less",
                                "TRACK_TITLE_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(TrackTitle other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
