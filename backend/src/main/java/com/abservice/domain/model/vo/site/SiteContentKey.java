package com.abservice.domain.model.vo.site;

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
 * サイト文言のキーの値オブジェクト
 *
 * <p>
 * どの文言かを指す識別子です。小文字英数字のセグメントをドットで区切った2セグメント以上の形（例: {@code site.name} /
 * {@code home.introduction}）に限ります。
 * </p>
 *
 * <p>
 * キーを列挙で閉じないのは、文言が今後増える前提を取るためです。列挙にすると1つ増やすたびに列挙の追加と
 * マイグレーションが必要になりますが、自由なキーならデータの投入だけで増やせます。形式を縛るのは、綴りの
 * 揺れ（大文字混在・区切り文字の不統一）で同じ文言が別キーになるのを防ぐためです。
 * </p>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            キー
 */
public record SiteContentKey(@NonNull String value) implements ValueObject<SiteContentKey> {
    /** キーの最大長（DB列に合わせる） */
    private static final int MAX_LENGTH = 100;

    /** 小文字英数字のセグメントをドットで区切った2セグメント以上 */
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+");

    /**
     * コンストラクタ
     *
     * @param value
     *            キー
     * @throws IllegalArgumentException
     *             空白のみ、最大長超過、形式違反のいずれかの場合
     */
    public SiteContentKey {
        keyPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            キー
     * @return SiteContentKeyインスタンス
     */
    public static @NonNull SiteContentKey of(@NonNull String value) {
        return new SiteContentKey(value);
    }

    /**
     * 外部入力（文字列）からキーを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。信頼できる内部生成には {@link #of(String)} を
     * 使用してください。
     * </p>
     *
     * @param value
     *            キーを表す文字列
     * @return 成功時は {@code SiteContentKey}、失敗時はエラー
     */
    public static Result<SiteContentKey> fromInput(@Nullable String value) {
        return keyPolicy().verify(value, SiteContentKey::new);
    }

    private static Policy<String> keyPolicy() {
        return Policy.all(
                Policy.of(
                        StringUtils::isNotBlank,
                        () -> new ErrorResult(
                                "value",
                                "Site content key cannot be blank",
                                "SITE_CONTENT_KEY_REQUIRED")),
                Policy.of(
                        (String v) -> StringUtils.length(v) <= MAX_LENGTH,
                        () -> new ErrorResult(
                                "value",
                                "Site content key must be " + MAX_LENGTH + " characters or less",
                                "SITE_CONTENT_KEY_TOO_LONG")),
                Policy.of(
                        SiteContentKey::matchesFormat,
                        () -> new ErrorResult(
                                "value",
                                "Site content key must be lowercase segments separated by dots (2 or more segments)",
                                "SITE_CONTENT_KEY_INVALID_FORMAT")));
    }

    private static boolean matchesFormat(@Nullable String value) {
        return Optional.ofNullable(value)
                .filter(v -> FORMAT.matcher(v).matches())
                .isPresent();
    }

    @Override
    public boolean equivalentTo(SiteContentKey other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
