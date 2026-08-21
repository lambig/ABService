package com.abservice.presentation.rest.exception;

import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 認証に失敗したリクエストを 401 の RFC 9457 Problem Details へ変換する JAX-RS ExceptionMapper
 *
 * <p>
 * 提示されたAPIキーが設定値と一致しない場合が該当する。失敗理由（キー不一致か未提示か）は応答に含めない。
 * </p>
 */
@Provider
public class AuthenticationFailedExceptionMapper implements ExceptionMapper<AuthenticationFailedException> {

    @Override
    public Response toResponse(AuthenticationFailedException exception) {
        return SecurityProblem.unauthorized("Authentication required");
    }
}
