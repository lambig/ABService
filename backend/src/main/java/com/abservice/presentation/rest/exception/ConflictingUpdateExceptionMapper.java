package com.abservice.presentation.rest.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

/**
 * 楽観ロックの競合を 409 Problem Details へ変換する JAX-RS ExceptionMapper
 *
 * <p>
 * 全テーブルが {@code version} 列を持ち、同一行への同時更新は意図して検出している（{@code docs/DECISIONS.md}
 * 5）。 検出した競合を専用マッパー無しで放置すると {@link UncaughtExceptionMapper} が 500 として返すため、
 * 「意図して導入した競合制御」が想定外障害として見えてしまう。競合はクライアントが読み直して再試行できる状態であり、 409 として返す。
 * </p>
 *
 * <p>
 * 応答には内部情報（SQL・エンティティ名・version値）を載せない。載せても呼び出し側の対処は「読み直して再試行」で
 * 変わらないうえ、テーブル構成が漏れる。
 * </p>
 *
 * <p>
 * Hibernate Reactive は flush 時の行数不一致を {@link OptimisticLockException}
 * へ変換して投げる（原因は {@code StaleStateException}）。変換されない経路のために
 * {@link StaleStateExceptionMapper} も併せて持つ。
 * </p>
 */
@Provider
public class ConflictingUpdateExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    /** 競合の安定したエラーコード（`type` URN の末尾になる） */
    static final String ERROR_CODE = "CONFLICTING_UPDATE";

    private static final String TITLE = "Conflicting update";

    private static final String DETAIL = "The resource was updated by another operation. Reload it and retry.";

    @Override
    public Response toResponse(OptimisticLockException exception) {
        return conflict();
    }

    /**
     * 競合を表す 409 応答を組み立てます。
     *
     * @return 409 の Problem Details 応答
     */
    static Response conflict() {
        final var problem = ProblemDetail.of(
                ERROR_CODE,
                TITLE,
                Response.Status.CONFLICT.getStatusCode(),
                DETAIL,
                List.of());
        return Response.status(problem.status())
                .type(MediaType.valueOf(ProblemDetail.MEDIA_TYPE))
                .entity(problem)
                .build();
    }
}
