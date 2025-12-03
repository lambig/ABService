package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.ValueObject;

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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Artist credit name cannot be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Artist credit name must be 255 characters or less");
        }
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
        if (other == null) {
            return false;
        }
        return this.value.equals(other.value);
    }
}
