package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * カタログナンバーの値オブジェクト
 *
 * <p>
 * アルバムのカタログナンバーを表す値オブジェクトです。 例: "ABC-0001", "XYZ-2024-01"
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は100文字です</li>
 * </ul>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            カタログナンバー（non-null）
 */
public record CatalogNumber(@NonNull String value) implements ValueObject<CatalogNumber> {
    /** カタログナンバーの最大長 */
    private static final int MAX_LENGTH = 100;

    /**
     * コンストラクタ
     *
     * @param value
     *            カタログナンバー（non-null）
     * @throws IllegalArgumentException
     *             カタログナンバーがnull、空白の場合、または最大長を超える場合
     */
    public CatalogNumber {
        catalogNumberPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            カタログナンバー（non-null）
     * @return CatalogNumberインスタンス
     */
    public static CatalogNumber of(@NonNull String value) {
        return new CatalogNumber(value);
    }

    /**
     * 外部入力（文字列）からカタログナンバーを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定や最大長超過は {@code Failure} として返します。
     * 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            カタログナンバーを表す文字列
     * @return 成功時は {@code CatalogNumber}、失敗時はエラー
     */
    public static Result<CatalogNumber> fromInput(@Nullable String value) {
        return catalogNumberPolicy().verify(value, CatalogNumber::new);
    }

    private static Policy<String> catalogNumberPolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "value",
                                "Catalog number cannot be blank",
                                "CATALOG_NUMBER_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult("value", "Catalog number must be " + MAX_LENGTH + " characters or less",
                                "CATALOG_NUMBER_TOO_LONG")));
    }

    @Override
    public boolean equivalentTo(CatalogNumber other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
