package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.vo.ValueObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.Optional;
import java.util.regex.Pattern;

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
 * @param value
 *            ISDN（13桁の数字）
 */
public record Isdn(String value) implements ValueObject<Isdn> {
    // ISDNのフォーマット（ハイフンあり/なし両方対応）
    // 278または279で始まる13桁の数字
    private static final Pattern ISDN_PATTERN = Pattern.compile("^27[89]-?\\d{1,5}-?\\d+-?\\d+-?\\d$");
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
        Validate.isTrue(StringUtils.isNotBlank(value), "ISDN cannot be blank");
        value = normalizeAndValidate(value);
    }

    /**
     * ファクトリメソッド
     *
     * @param value
     *            ISDN
     * @return Isdnインスタンス
     */
    public static Isdn of(String value) {
        return new Isdn(value);
    }

    private static String normalizeAndValidate(String value) {
        final var normalized = value.trim().replace("-", "");
        validateFormat(normalized, value);
        validateCheckDigit(normalized, value);
        return normalized;
    }

    private static void validateFormat(String normalized, String original) {
        Validate.isTrue(ISDN_SIMPLE_PATTERN.matcher(normalized).matches(),
                "ISDN must be 13 digits starting with 278 or 279 (hyphens optional). Got: %s", original);
    }

    private static void validateCheckDigit(String normalized, String original) {
        Validate.isTrue(isValidCheckDigit(normalized), "ISDN check digit is invalid: %s", original);
    }

    /**
     * チェックデジットの検証
     *
     * @param isdn
     *            13桁のISDN文字列
     * @return チェックデジットが正しければtrue
     */
    private static boolean isValidCheckDigit(String isdn) {
        if (isdn.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            final int digit = Character.getNumericValue(isdn.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        final int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == Character.getNumericValue(isdn.charAt(12));
    }

    /**
     * ハイフン付きフォーマットで取得 標準的な表示形式: 278-4-XXXXXX-XX-X
     *
     * @return ハイフン付きISDN（例: 278-4-702901-97-8）
     */
    public String formattedValue() {
        // 日本の場合: 278-4-XXXXXX-XX-X (3-1-7-2-1の構成)
        if (value.startsWith("2784") || value.startsWith("2794")) {
            return value.substring(0, 3) + "-" + value.substring(3, 4) + "-" + value.substring(4, 10) + "-"
                    + value.substring(10, 12) + "-" + value.substring(12, 13);
        }
        // その他の地域は簡略表示
        return value.substring(0, 3) + "-" + value.substring(3, 12) + "-" + value.substring(12, 13);
    }

    @Override
    public boolean equivalentTo(Isdn other) {
        return Optional.ofNullable(other).filter(o -> this.value.equals(o.value)).isPresent();
    }
}
