package com.abservice.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * グローバル例外ハンドラー
 */
@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Unhandled exception occurred", exception);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("status", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", "予期しないエラーが発生しました");

        // バリデーションエラーの場合
        if (exception instanceof ConstraintViolationException) {
            return handleConstraintViolationException((ConstraintViolationException) exception);
        }

        // IllegalArgumentExceptionの場合
        if (exception instanceof IllegalArgumentException) {
            errorResponse.put("status", Response.Status.BAD_REQUEST.getStatusCode());
            errorResponse.put("error", "Bad Request");
            errorResponse.put("message", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorResponse)
                    .build();
        }

        // NotFoundExceptionの場合
        if (exception instanceof jakarta.ws.rs.NotFoundException) {
            errorResponse.put("status", Response.Status.NOT_FOUND.getStatusCode());
            errorResponse.put("error", "Not Found");
            errorResponse.put("message", exception.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorResponse)
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errorResponse)
                .build();
    }

    /**
     * バリデーションエラーのハンドリング
     */
    private Response handleConstraintViolationException(ConstraintViolationException exception) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("status", Response.Status.BAD_REQUEST.getStatusCode());
        errorResponse.put("error", "Validation Error");
        errorResponse.put("message", "バリデーションエラーが発生しました");

        Map<String, String> validationErrors = new HashMap<>();
        Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();

        for (ConstraintViolation<?> violation : violations) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            validationErrors.put(propertyPath, message);
        }

        errorResponse.put("validationErrors", validationErrors);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse)
                .build();
    }
}


