package com.abservice.presentation.rest.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * どの専用マッパーも受け持たない例外を RFC 9457 Problem Details へ変換する最終フォールバック
 *
 * <p>
 * JAX-RS はより具体的な型のマッパーを選ぶため、ドメイン例外は {@link DomainExceptionMapper}、認証・認可の失敗は
 * それぞれの専用マッパーが受け持つ。ここへ到達するのは NPE やDB接続断のような想定外の例外と、JAX-RS 自身が投げる
 * {@link WebApplicationException}（未定義パスの404・非対応メソッドの405など）。
 * </p>
 *
 * <p>
 * 想定外の例外は内部情報を応答へ載せず、ログと突き合わせるための識別子だけを返す。
 * </p>
 */
@Provider
public class UncaughtExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UncaughtExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        return switch (exception) {
            case WebApplicationException web -> fromWebApplicationException(web);
            default -> unexpected(exception);
        };
    }

    /**
     * JAX-RS 自身が投げた例外を、応答本文を持たない場合のみ Problem Details へ整形します。
     *
     * @param exception
     *            JAX-RS 例外
     * @return 元のステータスを保った応答
     */
    private static Response fromWebApplicationException(WebApplicationException exception) {
        final var response = exception.getResponse();
        return Optional.of(response)
                .filter(Response::hasEntity)
                .orElseGet(
                        () -> problem(
                                ProblemDetail.of(
                                        "HTTP_%d".formatted(response.getStatus()),
                                        "HTTP error",
                                        response.getStatus(),
                                        exception.getMessage(),
                                        List.of())));
    }

    /**
     * 想定外の例外を 500 の Problem Details へ変換し、識別子付きでログへ記録します。
     *
     * @param exception
     *            想定外の例外
     * @return 識別子のみを載せた 500 応答
     */
    private static Response unexpected(Throwable exception) {
        final var incident = UUID.randomUUID().toString();
        LOG.errorf(
                exception,
                "Unhandled exception. incident=%s",
                incident);
        return problem(
                ProblemDetail.of(
                        "INTERNAL_ERROR",
                        "Internal server error",
                        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "Unexpected error occurred. incident=%s".formatted(incident),
                        List.of()));
    }

    private static Response problem(ProblemDetail problem) {
        return Response.status(problem.status())
                .type(MediaType.valueOf(ProblemDetail.MEDIA_TYPE))
                .entity(problem)
                .build();
    }
}
