package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.Function;

/**
 * カタログナンバーの値オブジェクト
 *
 * <p>
 * アルバムのカタログナンバーを表す値オブジェクトです。 例: "ABC-0001", "XYZ-2024-01"
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は100文字です</li>
 * </ul>
 *
 * @param value
 *            カタログナンバー（non-null）
 */
public record CatalogNumber(@NonNull String value) implements ValueObject<CatalogNumber> {
    /**
     * コンストラクタ
     *
     * @param value
     *            カタログナンバー（non-null）
     * @throws IllegalArgumentException
     *             カタログナンバーがnull、空白の場合、または最大長を超える場合
     */
    public CatalogNumber {
        Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "value",
                                "Catalog number cannot be blank",
                                "CATALOG_NUMBER_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= 100,
                        () -> new ErrorResult("value", "Catalog number must be 100 characters or less",
                                "CATALOG_NUMBER_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            カタログナンバー（non-null）
     * @return CatalogNumberインスタンス
     */
    public static CatalogNumber of(@NonNull String value) {
        return new CatalogNumber(value);
    }

    @Override
    public boolean equivalentTo(CatalogNumber other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
