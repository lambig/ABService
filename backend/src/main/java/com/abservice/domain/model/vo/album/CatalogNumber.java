package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.ValueObject;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Catalog number cannot be blank");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("Catalog number must be 100 characters or less");
        }
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
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
