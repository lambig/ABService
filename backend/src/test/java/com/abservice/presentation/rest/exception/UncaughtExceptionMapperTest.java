package com.abservice.presentation.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UncaughtExceptionMapper（未捕捉例外→RFC9457 Problem Details）のテスト")
class UncaughtExceptionMapperTest {

    @Test
    @DisplayName("想定外の例外は500・type=INTERNAL_ERROR・detailは識別子のみで例外メッセージを含まない")
    void unexpectedExceptionMapsTo500WithoutLeakingMessage() {
        final var response = new UncaughtExceptionMapper()
                .toResponse(new IllegalStateException("接続文字列 secret-value が不正です"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMediaType()).hasToString(ProblemDetail.MEDIA_TYPE);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
            assertThat(problem.type()).isEqualTo("urn:abservice:error:INTERNAL_ERROR");
            assertThat(problem.status()).isEqualTo(500);
            assertThat(problem.detail()).startsWith("Unexpected error occurred. incident=")
                    .doesNotContain("secret-value");
        });
    }

    @Test
    @DisplayName("本文を持たないJAX-RS例外は元のステータスを保ってProblem Details化される")
    void webApplicationExceptionKeepsItsStatus() {
        final var response = new UncaughtExceptionMapper().toResponse(new NotFoundException());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getMediaType()).hasToString(ProblemDetail.MEDIA_TYPE);
        assertThat(response.getEntity()).isInstanceOfSatisfying(
                ProblemDetail.class,
                problem -> assertThat(problem.type()).isEqualTo("urn:abservice:error:HTTP_404"));
    }

    @Test
    @DisplayName("本文を持つJAX-RS例外の応答はそのまま返す")
    void webApplicationExceptionWithEntityIsReturnedAsIs() {
        final var original = Response.status(Response.Status.CONFLICT).entity("already exists").build();

        final var response = new UncaughtExceptionMapper().toResponse(new WebApplicationException(original));

        assertThat(response).isSameAs(original);
        assertThat(response.getEntity()).isEqualTo("already exists");
    }
}
