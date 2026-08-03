package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import io.github.lambig.textescape.TextEscape;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * ISDN（国際標準同人誌番号）の値オブジェクト
 *
 * <p>
 * ISDNは13文字の数字で構成され、以下のフォーマットを持ちます： 278-4-XXXXXX-XX-X
 * </p>
 * <ul>
 * <li>フラグ: 278～279（3桁）</li>
 * <li>グループ記号: 1～5桁（可変、日本は4）</li>
 * <li>作品記号: 残りの桁数（可変）</li>
 * <li>チェックデジット: 最後の1桁</li>
 * </ul>
 * <p>
 * ハイフンは省略可能ですが、内部的には統一したフォーマットで保持します。
 * </p>
 *
 * <p>
 * 生成は2系統です。信頼できる内部生成には {@link #of(String)}（不正時は例外）を、外部入力からの生成には
 * {@link #fromInput(String)}（不正時は {@code Failure} を返す）を使用します。
 * </p>
 *
 * @param value
 *            ISDN（13桁の数字）
 */
public record Isdn(String value) implements ValueObject<Isdn> {
    /** ISDNのフォーマット（ハイフン除去後、278/279始まりの13桁） */
    private static final Pattern ISDN_SIMPLE_PATTERN = Pattern.compile("^27[89]\\d{10}$");

    /**
     * コンストラクタ
     *
     * @param value
     *            ISDN
     * @throws IllegalArgumentException
     *             ISDNがnullまたは不正なフォーマットの場合
     */
    public Isdn {
        blankPolicy().verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        value = normalizeAndValidate(value);
    }

    /**
     * ファクトリメソッド（内部生成用・不正時は例外）
     *
     * @param value
     *            ISDN
     * @return Isdnインスタンス
     */
    public static Isdn of(String value) {
        return new Isdn(value);
    }

    /**
     * 外部入力（文字列）からISDNを生成します。
     *
     * <p>
     * 例外をスローせず、検証結果を {@link Result} で返します。 未指定・不正フォーマット・チェックデジット不正は {@code Failure}
     * として返します。 信頼できる内部生成には {@link #of(String)} を使用してください。
     * </p>
     *
     * @param value
     *            ISDNを表す文字列（ハイフンは省略可）
     * @return 成功時は {@code Isdn}、失敗時はエラー
     */
    public static Result<Isdn> fromInput(@Nullable String value) {
        return blankPolicy().verify(value, Function.identity())
                .map(Isdn::cleanse)
                .flatMap(normalized -> validateFormat(normalized, value))
                .flatMap(normalized -> validateCheckDigit(normalized, value))
                .map(Isdn::new);
    }

    private static Policy<String> blankPolicy() {
        return Policy.of(
                StringUtils::isNotBlank,
                () -> new ErrorResult(
                        "value",
                        "ISDN cannot be blank",
                        "ISDN_REQUIRED"));
    }

    private static String cleanse(String raw) {
        return raw.trim().replace("-", "");
    }

    private static String normalizeAndValidate(String value) {
        final var normalized = cleanse(value);
        validateFormat(normalized, value)
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        validateCheckDigit(normalized, value)
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        return normalized;
    }

    private static Result<String> validateFormat(String normalized, @Nullable String original) {
        return Policy.of(
                (String v) -> ISDN_SIMPLE_PATTERN.matcher(v).matches(),
                () -> new ErrorResult("value",
                        "ISDN must be 13 digits starting with 278 or 279 (hyphens optional). Got: " + original,
                        "ISDN_INVALID_FORMAT"))
                .verify(normalized, Function.identity());
    }

    private static Result<String> validateCheckDigit(String normalized, @Nullable String original) {
        return Policy.<String>of(
                Isdn::isValidCheckDigit,
                () -> new ErrorResult(
                        "value",
                        "ISDN check digit is invalid: " + original,
                        "ISDN_INVALID_CHECK_DIGIT"))
                .verify(normalized, Function.identity());
    }

    /**
     * チェックデジットの検証
     *
     * @param isdn
     *            13桁のISDN文字列
     * @return チェックデジットが正しければtrue
     */
    private static boolean isValidCheckDigit(String isdn) {
        return Optional.ofNullable(isdn)
                .filter(s -> s.length() == 13)
                .filter(Isdn::checkDigitMatches)
                .isPresent();
    }

    private static boolean checkDigitMatches(String isdn) {
        return computeCheckDigit(isdn) == Character.getNumericValue(isdn.charAt(12));
    }

    private static int computeCheckDigit(String isdn) {
        return (10 - (IntStream.range(0, 12)
                .map(
                        i -> Character.getNumericValue(isdn.charAt(i)) * ((i % 2 == 0)
                                ? 1
                                : 3))
                .sum() % 10)) % 10;
    }

    /**
     * ハイフン付きフォーマットで取得。 フラグが2784/2794（日本）の場合は3-1-6-2-1区切り（278-4-XXXXXX-XX-X）、
     * それ以外の地域は簡略表示（フラグ-本体-チェックデジット）
     *
     * @return ハイフン付きISDN（例: 278-4-702901-97-8）
     */
    public String formattedValue() {
        return TextEscape.escape("${flag}-${body}-${check}")
                .where("flag", value.substring(0, 3))
                .where(
                        "body",
                        Stream.of("2784", "2794")
                                .anyMatch(value::startsWith)
                                        ? value.substring(3, 4) + "-" + value.substring(4, 10) + "-"
                                                + value.substring(10, 12)
                                        : value.substring(3, 12))
                .where("check", value.substring(12, 13)).compile();
    }

    @Override
    public boolean equivalentTo(Isdn other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
