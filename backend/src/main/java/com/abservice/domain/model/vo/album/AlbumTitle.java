package com.abservice.domain.model.vo.album;

import static java.util.function.Predicate.not;

import com.abservice.domain.model.vo.ValueObject;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

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
public record AlbumTitle(@NonNull String value) implements ValueObject<AlbumTitle> {
    /**
     * コンストラクタ
     *
     * @param value
     *            アルバムタイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public AlbumTitle {
        Optional.ofNullable(value).filter(not(String::isBlank))
                .orElseThrow(() -> new IllegalArgumentException("Album title cannot be blank"));
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
    public static @NonNull AlbumTitle of(@NonNull String value) {
        return new AlbumTitle(value);
    }

    @Override
    public boolean equivalentTo(AlbumTitle other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
