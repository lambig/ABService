package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

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
 * @param value
 *            URL文字列
 */
public record Url(String value) implements ValueObject<Url> {
    /** URL文字列の最大長 */
    private static final int MAX_LENGTH = 500;

    /**
     * コンストラクタ
     *
     * @param value
     *            URL文字列
     * @throws IllegalArgumentException
     *             URLがnullまたは空白の場合、最大長を超える場合、または不正なフォーマットの場合
     */
    public Url {
        Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "url",
                                "URL cannot be blank",
                                "URL_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult("url", "URL must be " + MAX_LENGTH + " characters or less",
                                "URL_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        validateUriFormat(value);
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            URL文字列
     * @return Urlインスタンス
     */
    public static Url of(String value) {
        return new Url(value);
    }

    private static void validateUriFormat(String value) {
        // URI.create は不正な構文の場合に IllegalArgumentException（URISyntaxException を内包）を送出する
        URI.create(value);
    }

    @Override
    public boolean equivalentTo(Url other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
