package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.ValueObject;

/**
 * クレジット（作曲者・アレンジャー等）の値オブジェクト
 *
 * <p>
 * 作曲者、アレンジャー、その他のクレジット情報を表す値オブジェクトです。 例: "Trad.", "John Doe", "Jane Smith arr."
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * @param value
 *            クレジット
 */
public record Credit(String value) implements ValueObject<Credit> {
    /**
     * コンストラクタ
     *
     * @param value
     *            クレジット
     * @throws IllegalArgumentException
     *             クレジットがnullまたは空白の場合、または最大長を超える場合
     */
    public Credit {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Credit cannot be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Credit must be 255 characters or less");
        }
    }

    @Override
    public boolean equivalentTo(Credit other) {
        if (other == null) {
            return false;
        }
        return this.value.equals(other.value);
    }
}
