package com.abservice.domain.model.vo.album;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.vo.ValueObject;
import com.abservice.lib.ErrorResult;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * ISRC（国際標準レコーディングコード）の値オブジェクト
 *
 * <p>
 * ISRCは12文字の英数字で構成され、以下のフォーマットを持ちます： CC-XXX-YY-NNNNN
 * </p>
 * <ul>
 * <li>CC: 国コード（2文字）</li>
 * <li>XXX: 登録者コード（3文字）</li>
 * <li>YY: 年（2桁）</li>
 * <li>NNNNN: 指定コード（5桁）</li>
 * </ul>
 * <p>
 * ハイフンは省略可能ですが、内部的には統一したフォーマットで保持します。
 * </p>
 *
 * @param value
 *            ISRC
 */
public record Isrc(String value) implements ValueObject<Isrc> {
    // ISRCのフォーマット（ハイフンあり/なし両方対応）
    private static final Pattern ISRC_PATTERN = Pattern.compile("^[A-Z]{2}-?[A-Z0-9]{3}-?[0-9]{2}-?[0-9]{5}$");

    /**
     * コンストラクタ
     *
     * @param value
     *            ISRC
     * @throws IllegalArgumentException
     *             ISRCがnullまたは不正なフォーマットの場合
     */
    public Isrc {
        Policy.<String>of(
                StringUtils::isNotBlank,
                () -> new ErrorResult(
                        "value",
                        "ISRC cannot be blank",
                        "ISRC_REQUIRED"))
                .verify(value, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        final var normalized = value.toUpperCase().trim();
        Policy.of(
                (String v) -> ISRC_PATTERN.matcher(v).matches(),
                () -> new ErrorResult("value", "ISRC must match the format: CC-XXX-YY-NNNNN (hyphens optional)",
                        "ISRC_INVALID_FORMAT"))
                .verify(normalized, Function.identity())
                .resolve(errors -> new IllegalArgumentException(errors.getFirst().message()));
        value = normalized.replace("-", ""); // ハイフンを除去して統一フォーマットで保持
    }

    /**
     * ハイフン付きフォーマットで取得
     *
     * @return ハイフン付きISRC（例: US-ABC-12-34567）
     */
    public String formattedValue() {
        return value.substring(0, 2) + "-" + value.substring(2, 5) + "-" + value.substring(5, 7) + "-"
                + value.substring(7, 12);
    }

    @Override
    public boolean equivalentTo(Isrc other) {
        return Optional.ofNullable(other)
                .filter(o -> this.value.equals(o.value))
                .isPresent();
    }
}
