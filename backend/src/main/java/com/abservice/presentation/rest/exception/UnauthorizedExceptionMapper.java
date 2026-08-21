package com.abservice.presentation.rest.exception;

import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 認証されていないリクエストを 401 の RFC 9457 Problem Details へ変換する JAX-RS ExceptionMapper
 *
 * <p>
 * APIキーを提示せずに管理操作のエンドポイントへアクセスした場合が該当する。
 * </p>
 */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

    @Override
    public Response toResponse(UnauthorizedException exception) {
        return SecurityProblem.unauthorized("Authentication required");
    }
}
