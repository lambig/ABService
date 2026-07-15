package com.abservice.presentation.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.domain.exception.BusinessRuleViolationException;
import com.abservice.domain.exception.DomainException;
import com.abservice.domain.exception.EntityNotFoundException;
import com.abservice.domain.exception.ValidationException;
import com.abservice.lib.ErrorResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DomainExceptionMapper（例外→RFC9457 Problem Details）のテスト")
class DomainExceptionMapperTest {

    @Test
    @DisplayName("ValidationExceptionは400・type=VALIDATION_ERROR・errors付き")
    void validationMapsTo400WithErrors() {
        final var exception = new ValidationException(List.of(
                new ErrorResult(
                        "title",
                        "記事タイトルは必須です",
                        "ARTICLE_TITLE_REQUIRED")));

        final var problem = DomainExceptionMapper.toProblem(exception);

        assertThat(problem.status()).isEqualTo(400);
        assertThat(problem.type()).isEqualTo("urn:abservice:error:VALIDATION_ERROR");
        assertThat(problem.errors()).singleElement().satisfies(e -> {
            assertThat(e.field()).isEqualTo("title");
            assertThat(e.code()).isEqualTo("ARTICLE_TITLE_REQUIRED");
        });
    }

    @Test
    @DisplayName("EntityNotFoundExceptionは404・errorsは空")
    void notFoundMapsTo404() {
        final var problem = DomainExceptionMapper.toProblem(new EntityNotFoundException("見つかりません"));

        assertThat(problem.status()).isEqualTo(404);
        assertThat(problem.type()).isEqualTo("urn:abservice:error:ENTITY_NOT_FOUND");
        assertThat(problem.errors()).isEmpty();
    }

    @Test
    @DisplayName("BusinessRuleViolationExceptionは409")
    void businessRuleMapsTo409() {
        final var problem = DomainExceptionMapper.toProblem(new BusinessRuleViolationException("重複"));

        assertThat(problem.status()).isEqualTo(409);
        assertThat(problem.type()).isEqualTo("urn:abservice:error:BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("その他のDomainExceptionは500・type=errorCode")
    void otherDomainExceptionMapsTo500() {
        final var problem = DomainExceptionMapper.toProblem(new DomainException("CUSTOM_CODE", "内部エラー") {
        });

        assertThat(problem.status()).isEqualTo(500);
        assertThat(problem.type()).isEqualTo("urn:abservice:error:CUSTOM_CODE");
    }
}
