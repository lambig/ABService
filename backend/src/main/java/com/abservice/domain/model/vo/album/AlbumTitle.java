package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.ValueObject;

/**
 * アルバムタイトルの値オブジェクト
 *
 * <p>
 * アルバムのタイトルを表す値オブジェクトです。 以下の制約を持ちます：
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * @param value
 *            アルバムタイトル
 */
public record AlbumTitle(String value) implements ValueObject<AlbumTitle> {
    /**
     * コンストラクタ
     *
     * @param value
     *            アルバムタイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public AlbumTitle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Album title cannot be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Album title must be 255 characters or less");
        }
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            アルバムタイトル
     * @return AlbumTitleインスタンス
     */
    public static AlbumTitle of(String value) {
        return new AlbumTitle(value);
    }

    @Override
    public boolean equivalentTo(AlbumTitle other) {
        if (other == null) {
            return false;
        }
        return this.value.equals(other.value);
    }
}
