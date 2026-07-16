package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.function.Function;

/**
 * アーティスト名義の値オブジェクト
 *
 * <p>
 * アーティストの表記名を表す値オブジェクトです。 例: "Foo Bar", "Foo Bar feat. Baz"
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * @param value
 *            アーティスト名義
 */
public record ArtistCreditName(String value) implements ValueObject<ArtistCreditName> {
    /**
     * コンストラクタ
     *
     * @param value
     *            アーティスト名義
     * @throws IllegalArgumentException
     *             名義がnullまたは空白の場合、または最大長を超える場合
     */
    public ArtistCreditName {
        Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult("artistCreditName", "Artist credit name cannot be blank",
                                "ARTIST_CREDIT_NAME_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= 255,
                        () -> new ErrorResult("artistCreditName", "Artist credit name must be 255 characters or less",
                                "ARTIST_CREDIT_NAME_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            アーティスト名義
     * @return ArtistCreditNameインスタンス
     */
    public static ArtistCreditName of(String value) {
        return new ArtistCreditName(value);
    }

    @Override
    public boolean equivalentTo(ArtistCreditName other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
