package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.ValueObject;

import java.net.URI;
import java.net.URISyntaxException;

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
    /**
     * コンストラクタ
     *
     * @param value
     *            URL文字列
     * @throws IllegalArgumentException
     *             URLがnullまたは空白の場合、最大長を超える場合、または不正なフォーマットの場合
     */
    public Url {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("URL cannot be blank");
        }
        if (value.length() > 500) {
            throw new IllegalArgumentException("URL must be 500 characters or less");
        }
        try {
            new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + value, e);
        }
    }

    @Override
    public boolean equivalentTo(Url other) {
        if (other == null) {
            return false;
        }
        return this.value.equals(other.value);
    }
}
