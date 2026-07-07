package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.vo.ValueObject;

import java.net.URI;
import java.util.Optional;

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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("URL cannot be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("URL must be " + MAX_LENGTH + " characters or less");
        }
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
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
