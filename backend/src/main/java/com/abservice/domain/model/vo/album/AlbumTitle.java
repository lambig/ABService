package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
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
        Policy.all(
                Policy.of(StringUtils::isNotBlank,
                        () -> new ErrorResult("value", "Album title cannot be blank", "ALBUM_TITLE_REQUIRED")),
                Policy.of((String v) -> StringUtils.length(v) <= 255,
                        () -> new ErrorResult("value", "Album title must be 255 characters or less",
                                "ALBUM_TITLE_TOO_LONG")))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
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
