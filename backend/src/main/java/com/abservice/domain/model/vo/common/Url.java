package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * URLの値オブジェクト
 *
 * <p>
 * 外部リンクなどのURLを表す値オブジェクトです。 バリデーションにより正しいURLフォーマットであることを保証します。
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は500文字です</li>
 * <li>正しいURLフォーマットである必要があります</li>
 * </ul>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            URL文字列
 */
public record Url(String value) implements ValueObject<Url> {
    /** URL文字列の最大長 */
    private static final int MAX_LENGTH = 500;

    /** URIのスキーム部分（例: {@code https:}, {@code mailto:}）の簡易フォーマット */
    private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.+$");

    /**
     * コンストラクタ
     *
     * @param value
     *            URL文字列
     * @throws IllegalArgumentException
     *             URLがnullまたは空白の場合、最大長を超える場合、または不正なフォーマットの場合
     */
    public Url {
        urlPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            URL文字列
     * @return Urlインスタンス
     */
    public static Url of(String value) {
        return new Url(value);
    }

    /**
     * 外部入力（文字列）からURLを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定・最大長超過・不正フォーマットは {@code Failure}
     * として返します。 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            URL文字列を表す文字列
     * @return 成功時は {@code Url}、失敗時はエラー
     */
    public static Result<Url> fromInput(@Nullable String value) {
        return urlPolicy().verify(value, Url::new);
    }

    private static Policy<String> urlPolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "url",
                                "URL cannot be blank",
                                "URL_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult("url", "URL must be " + MAX_LENGTH + " characters or less",
                                "URL_TOO_LONG")),
                Policy.of(
                        Url::hasValidFormat,
                        () -> new ErrorResult("url", "URL format is invalid",
                                "URL_INVALID_FORMAT")));
    }

    private static boolean hasValidFormat(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(v -> URI_SCHEME_PATTERN.matcher(v).matches())
                .isPresent();
    }

    @Override
    public boolean equivalentTo(Url other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
