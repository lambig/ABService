package com.abservice.presentation.rest.exception;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.DomainException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

/**
 * {@link DomainException} を RFC 9457 Problem
 * Details（{@code application/problem+json}）へ変換する JAX-RS ExceptionMapper
 *
 * <p>
 * {@link ValidationException}→400（{@code errors}
 * 付き）、{@link EntityNotFoundException}→404、
 * {@link BusinessRuleViolationException}→409、その他の {@link DomainException}→500
 * に対応づける。
 * </p>
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    /** application/problem+json（RFC 9457） */
    private static final String PROBLEM_JSON = "application/problem+json";

    @Override
    public Response toResponse(DomainException exception) {
        final ProblemDetail body = switch (exception) {
            case ValidationException validation -> ProblemDetail.of(
                    validation.errorCode(),
                    "Validation failed",
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    validation.getMessage(),
                    toFieldErrors(validation.errors()));
            case EntityNotFoundException notFound -> ProblemDetail.of(
                    notFound.errorCode(),
                    "Resource not found",
                    Response.Status.NOT_FOUND.getStatusCode(),
                    notFound.getMessage(),
                    List.of());
            case BusinessRuleViolationException conflict -> ProblemDetail.of(
                    conflict.errorCode(),
                    "Business rule violation",
                    Response.Status.CONFLICT.getStatusCode(),
                    conflict.getMessage(),
                    List.of());
            default -> ProblemDetail.of(
                    exception.errorCode(),
                    "Internal server error",
                    Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    exception.getMessage(),
                    List.of());
        };
        return Response.status(body.status()).type(MediaType.valueOf(PROBLEM_JSON)).entity(body).build();
    }

    private static List<FieldError> toFieldErrors(List<ErrorResult> errors) {
        return errors.stream().map(
                e -> new FieldError(
                        e.field(),
                        e.message(),
                        e.code()))
                .toList();
    }
}
