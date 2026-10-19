package com.abservice.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** 受付超過・待機期限切れを、内部状態を漏らさず再試行可能な応答へ写す。 */
@Provider
@APIResponse(responseCode = "503")
public class FaultToleranceExceptionMapper implements ExceptionMapper<FaultToleranceException> {
    @Override
    public Response toResponse(FaultToleranceException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(ProblemDetail.MEDIA_TYPE).header("Retry-After", "1").header("Cache-Control", "no-store")
                .entity(
                        ProblemDetail.of(
                                "PREPARATION_BUSY",
                                "Service temporarily unavailable",
                                503,
                                "準備状態を取得できませんでした。少し待って一覧を更新してください。",
                                List.of()))
                .build();
    }
}
