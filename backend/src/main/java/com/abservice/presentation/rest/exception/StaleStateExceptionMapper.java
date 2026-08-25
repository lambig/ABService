package com.abservice.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.StaleStateException;

/**
 * Hibernate の競合例外が JPA 例外へ変換されずに表へ出た場合も 409 とするマッパー
 *
 * <p>
 * 競合の意味と応答は {@link ConflictingUpdateExceptionMapper} と同一。変換されるかどうかは Hibernate の
 * 経路（flush か、バッチか、明示ロックか）によって変わるため、根の型でも受けておく。
 * </p>
 */
@Provider
public class StaleStateExceptionMapper implements ExceptionMapper<StaleStateException> {

    @Override
    public Response toResponse(StaleStateException exception) {
        return ConflictingUpdateExceptionMapper.conflict();
    }
}
