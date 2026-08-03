package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

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
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            アーティスト名義
 */
public record ArtistCreditName(String value) implements ValueObject<ArtistCreditName> {
    /** アーティスト名義の最大長 */
    private static final int MAX_LENGTH = 255;

    /**
     * コンストラクタ
     *
     * @param value
     *            アーティスト名義
     * @throws IllegalArgumentException
     *             名義がnullまたは空白の場合、または最大長を超える場合
     */
    public ArtistCreditName {
        namePolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            アーティスト名義
     * @return ArtistCreditNameインスタンス
     */
    public static ArtistCreditName of(String value) {
        return new ArtistCreditName(value);
    }

    /**
     * 外部入力（文字列）からアーティスト名義を生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            アーティスト名義を表す文字列
     * @return 成功時は {@code ArtistCreditName}、失敗時はエラー
     */
    public static Result<ArtistCreditName> fromInput(@Nullable String value) {
        return namePolicy().verify(value, ArtistCreditName::new);
    }

    private static Policy<String> namePolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult("artistCreditName", "Artist credit name cannot be blank",
                                "ARTIST_CREDIT_NAME_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult("artistCreditName",
                                "Artist credit name must be " + MAX_LENGTH + " characters or less",
                                "ARTIST_CREDIT_NAME_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(ArtistCreditName other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
