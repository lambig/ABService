package com.abservice.presentation.rest.exception;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * 認証・認可の失敗を RFC 9457 Problem Details へ変換する共通処理
 *
 * <p>
 * ドメイン例外（{@link DomainExceptionMapper}）と同じ {@code application/problem+json}
 * 形状に揃え、 クライアントが全エラーを同一の形で扱えるようにする。
 * </p>
 */
final class SecurityProblem {

    /** application/problem+json（RFC 9457） */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** 401 応答に付与する認証要求。realm はサービス名固定 */
    private static final String CHALLENGE = "Bearer realm=\"abservice\"";

    private SecurityProblem() {
    }

    /**
     * 認証されていない・APIキーが不正な場合の 401 応答を生成します。
     *
     * @param detail
     *            発生固有の説明
     * @return 401 の Problem Details 応答（{@code WWW-Authenticate} 付き）
     */
    static Response unauthorized(String detail) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE)
                .type(MediaType.valueOf(PROBLEM_JSON))
                .entity(
                        ProblemDetail.of(
                                "UNAUTHORIZED",
                                "Unauthorized",
                                Response.Status.UNAUTHORIZED.getStatusCode(),
                                detail,
                                List.of()))
                .build();
    }

    /**
     * 認証済みだが権限が不足する場合の 403 応答を生成します。
     *
     * @param detail
     *            発生固有の説明
     * @return 403 の Problem Details 応答
     */
    static Response forbidden(String detail) {
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.valueOf(PROBLEM_JSON))
                .entity(
                        ProblemDetail.of(
                                "FORBIDDEN",
                                "Forbidden",
                                Response.Status.FORBIDDEN.getStatusCode(),
                                detail,
                                List.of()))
                .build();
    }
}
