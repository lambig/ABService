package com.abservice.presentation.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.StaleStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("楽観ロック競合→409 Problem Details のテスト")
class ConflictingUpdateExceptionMapperTest {

    @Test
    @DisplayName("楽観ロック競合は409・type=CONFLICTING_UPDATEで返る")
    void optimisticLockExceptionMapsTo409() {
        final var response = new ConflictingUpdateExceptionMapper()
                .toResponse(new OptimisticLockException("row was updated"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getMediaType()).hasToString(ProblemDetail.MEDIA_TYPE);
        assertThat(response.getEntity()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
            assertThat(problem.type()).isEqualTo("urn:abservice:error:CONFLICTING_UPDATE");
            assertThat(problem.status()).isEqualTo(409);
            assertThat(problem.errors()).isEmpty();
        });
    }

    @Test
    @DisplayName("競合の応答は内部情報（SQL・エンティティ名・version）を含まない")
    void conflictResponseDoesNotLeakInternals() {
        final var response = new ConflictingUpdateExceptionMapper()
                .toResponse(
                        new OptimisticLockException(
                                "Unexpected row count (expected 1 but was 0)"
                                        + " [update article set version=? where article_id=? and version=?]",
                                new StaleObjectStateException("ArticleTableRecord", 42L)));

        assertThat(response.getEntity()).isInstanceOfSatisfying(
                ProblemDetail.class,
                problem -> assertThat(problem.detail())
                        .doesNotContain("update article")
                        .doesNotContain("ArticleTableRecord")
                        .doesNotContain("version")
                        .isEqualTo("The resource was updated by another operation. Reload it and retry."));
    }

    @Test
    @DisplayName("JPA例外へ変換されないHibernateの競合例外も同じ409で返る")
    void staleStateExceptionMapsToSameConflict() {
        final var response = new StaleStateExceptionMapper()
                .toResponse(new StaleStateException("Unexpected row count"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getEntity()).isInstanceOfSatisfying(
                ProblemDetail.class,
                problem -> assertThat(problem.type()).isEqualTo("urn:abservice:error:CONFLICTING_UPDATE"));
    }
}
