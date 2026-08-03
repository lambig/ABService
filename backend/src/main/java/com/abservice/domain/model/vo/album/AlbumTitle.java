package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            アルバムタイトル
 */
public record AlbumTitle(@NonNull String value) implements ValueObject<AlbumTitle> {
    /** アルバムタイトルの最大長 */
    private static final int MAX_LENGTH = 255;

    /**
     * コンストラクタ
     *
     * @param value
     *            アルバムタイトル
     * @throws IllegalArgumentException
     *             タイトルがnullまたは空白の場合、または最大長を超える場合
     */
    public AlbumTitle {
        titlePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            アルバムタイトル
     * @return AlbumTitleインスタンス
     */
    public static @NonNull AlbumTitle of(@NonNull String value) {
        return new AlbumTitle(value);
    }

    /**
     * 外部入力（文字列）からアルバムタイトルを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            アルバムタイトルを表す文字列
     * @return 成功時は {@code AlbumTitle}、失敗時はエラー
     */
    public static Result<AlbumTitle> fromInput(@Nullable String value) {
        return titlePolicy().verify(value, AlbumTitle::new);
    }

    private static Policy<String> titlePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "value",
                                "Album title cannot be blank",
                                "ALBUM_TITLE_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult("value", "Album title must be " + MAX_LENGTH + " characters or less",
                                "ALBUM_TITLE_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(AlbumTitle other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
