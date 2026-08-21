package com.abservice.domain.model.vo.common;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * アセットキーの値オブジェクト
 *
 * <p>
 * アップロード済みアセット（画像等）の保管キーを表す値オブジェクトです。ドメインは配信URLではなくキーだけを持ち、配信URLは
 * 照会時に配信設定から組み立てます（環境やCDNのパス構成に保存データが依存しないようにするため）。
 * </p>
 * <ul>
 * <li>nullまたは空白文字のみは許可されません</li>
 * <li>最大長は255文字です</li>
 * <li>キーはパスではないため、英数字と {@code . _ -} のみで構成される必要があります（スラッシュ・上位参照を含められません）</li>
 * </ul>
 *
 * <p>
 * どの画像形式を受け入れるか・キーをどう採番するかはアップロード基盤（アプリケーション層）の関心であり、本VOは「パスではない
 * 非空のキーである」ことだけを保証します。
 * </p>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            アセットキー（non-null）
 */
public record AssetKey(@NonNull String value) implements ValueObject<AssetKey> {
    /** アセットキーの最大長 */
    private static final int MAX_LENGTH = 255;

    /** アセットキーとして許可する文字構成（パス区切り・上位参照を含められない） */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    /**
     * コンストラクタ
     *
     * @param value
     *            アセットキー（non-null）
     * @throws IllegalArgumentException
     *             キーがnull、空白の場合、最大長を超える場合、または許可されない文字を含む場合
     */
    public AssetKey {
        assetKeyPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            アセットキー（non-null）
     * @return AssetKeyインスタンス
     */
    public static AssetKey of(@NonNull String value) {
        return new AssetKey(value);
    }

    /**
     * 外部入力（文字列）からアセットキーを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。未指定・最大長超過・不正な文字構成は {@code Failure}
     * として返します。信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            アセットキーを表す文字列
     * @return 成功時は {@code AssetKey}、失敗時はエラー
     */
    public static Result<AssetKey> fromInput(@Nullable String value) {
        return assetKeyPolicy().verify(value, AssetKey::new);
    }

    private static Policy<String> assetKeyPolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "assetKey",
                                "Asset key cannot be blank",
                                "ASSET_KEY_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult(
                                "assetKey",
                                "Asset key must be " + MAX_LENGTH + " characters or less",
                                "ASSET_KEY_TOO_LONG")),
                Policy.of(
                        AssetKey::hasValidFormat,
                        () -> new ErrorResult(
                                "assetKey",
                                "Asset key must not contain path separators",
                                "ASSET_KEY_INVALID_FORMAT")));
    }

    private static boolean hasValidFormat(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(v -> KEY_PATTERN.matcher(v).matches())
                .isPresent();
    }

    @Override
    public boolean equivalentTo(AssetKey other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
