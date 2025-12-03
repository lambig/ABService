package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.ValueObject;

import java.util.Optional;

/**
 * トラックタイトルの値オブジェクト
 *
 * <p>
 * トラックのタイトルを表す値オブジェクトです。 以下の制約を持ちます：
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * </ul>
 *
 * @param value
 *            トラックタイトル
 */
public record TrackTitle(String value) implements ValueObject<TrackTitle> {
    /**
     * コンストラクタ
     *
     * @param value
     *            トラックタイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public TrackTitle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Track title cannot be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Track title must be 255 characters or less");
        }
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            トラックタイトル
     * @return TrackTitleインスタンス
     */
    public static TrackTitle of(String value) {
        return new TrackTitle(value);
    }

    @Override
    public boolean equivalentTo(TrackTitle other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
