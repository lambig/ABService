package com.abservice.presentation.rest.exception;

import io.quarkus.security.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 権限不足のリクエストを 403 の RFC 9457 Problem Details へ変換する JAX-RS ExceptionMapper
 *
 * <p>
 * 認証は成功したが要求ロールを持たない場合が該当する。
 * </p>
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException exception) {
        return SecurityProblem.forbidden("Insufficient permissions");
    }
}
