package com.abservice.presentation.rest.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * RFC 9457（Problem Details for HTTP APIs）準拠のエラー表現
 *
 * <p>
 * メディアタイプは {@code application/problem+json}。{@code type} は problem type を識別する
 * URI で、本 API では ドメインのエラーコードを URN 化して載せる（機械可読コードを別フィールドで重複させない）。{@code errors}
 * は検証エラー時のみ 付与する拡張メンバ。空の場合は出力しない。
 * </p>
 *
 * @param type
 *            problem type を識別する URI（例:
 *            {@code urn:abservice:error:VALIDATION_ERROR}）
 * @param title
 *            problem type の短い定型サマリ
 * @param status
 *            HTTP ステータスコード
 * @param detail
 *            この発生に固有の人間可読な説明（nullable）
 * @param errors
 *            フィールド単位の検証エラー（検証時のみ。空なら出力しない）
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProblemDetail(
        String type,
        String title,
        int status,
        @Nullable String detail,
        List<FieldError> errors) {

    /** application/problem+json（RFC 9457） */
    public static final String MEDIA_TYPE = "application/problem+json";

    private static final String TYPE_PREFIX = "urn:abservice:error:";

    /**
     * ドメインのエラーコードを {@code type} URN 化して Problem Details を生成します。
     *
     * @param errorCode
     *            ドメインの機械可読エラーコード
     * @param title
     *            problem type の短いサマリ
     * @param status
     *            HTTP ステータスコード
     * @param detail
     *            発生固有の説明（nullable）
     * @param errors
     *            フィールド単位の検証エラー（なければ空リスト）
     * @return Problem Details
     */
    public static ProblemDetail of(
            String errorCode,
            String title,
            int status,
            @Nullable String detail,
            List<FieldError> errors) {
        return new ProblemDetail(
                TYPE_PREFIX + errorCode,
                title,
                status,
                detail,
                errors);
    }
}
