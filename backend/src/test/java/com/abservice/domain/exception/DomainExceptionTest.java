package com.abservice.domain.exception;

import static org.assertj.core.api.Assertions.*;

import com.abservice.lib.ErrorResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ドメイン例外階層のテスト")
class DomainExceptionTest {

    @Nested
    @DisplayName("DomainException（基底）")
    class BaseTest {

        @Test
        @DisplayName("サブクラスはDomainExceptionかつRuntimeExceptionである")
        void subclassesAreDomainAndRuntimeException() {
            // Arrange
            final DomainException validation = new ValidationException(List.of(new ErrorResult("f", "m")));
            final DomainException notFound = new EntityNotFoundException("not found");
            final DomainException businessRule = new BusinessRuleViolationException("violated");

            // Assert
            assertThat(validation).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
            assertThat(notFound).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
            assertThat(businessRule).isInstanceOf(DomainException.class).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("ValidationException")
    class ValidationExceptionTest {

        @Test
        @DisplayName("メッセージとエラーリストを保持する")
        void holdsMessageAndErrors() {
            // Arrange
            final ErrorResult error1 = new ErrorResult("title", "必須です");
            final ErrorResult error2 = new ErrorResult("catalogNumber", "形式が不正です");

            // Act
            final ValidationException ex = new ValidationException("入力が不正です", List.of(error1, error2));

            // Assert
            assertThat(ex.getMessage()).isEqualTo("入力が不正です");
            assertThat(ex.errorCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(ex.errors()).containsExactly(error1, error2);
        }

        @Test
        @DisplayName("エラーリストのみ指定するとメッセージを組み立てる")
        void buildsMessageFromErrors() {
            // Arrange
            final ErrorResult error1 = new ErrorResult("title", "必須です");
            final ErrorResult error2 = new ErrorResult(
                    "catalogNumber",
                    "形式が不正です",
                    "E002");

            // Act
            final ValidationException ex = new ValidationException(List.of(error1, error2));

            // Assert
            assertThat(ex.getMessage()).isEqualTo("title: 必須です, catalogNumber: 形式が不正です (code: E002)");
            assertThat(ex.errors()).containsExactly(error1, error2);
        }

        @Test
        @DisplayName("保持するエラーリストは不変である")
        void errorsListIsUnmodifiable() {
            // Arrange
            final ValidationException ex = new ValidationException(List.of(new ErrorResult("f", "m")));

            // Act & Assert
            assertThatThrownBy(() -> ex.errors().add(new ErrorResult("x", "y")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("EntityNotFoundException")
    class EntityNotFoundExceptionTest {

        @Test
        @DisplayName("メッセージとエラーコードを保持する")
        void holdsMessageAndErrorCode() {
            // Act
            final EntityNotFoundException ex = new EntityNotFoundException("Article が見つかりません");

            // Assert
            assertThat(ex.getMessage()).isEqualTo("Article が見つかりません");
            assertThat(ex.errorCode()).isEqualTo("ENTITY_NOT_FOUND");
        }

        @Test
        @DisplayName("of()でエンティティ名とIDからメッセージを組み立てる")
        void ofBuildsMessage() {
            // Act
            final EntityNotFoundException ex = EntityNotFoundException.of("Article", "abc-123");

            // Assert
            assertThat(ex.getMessage()).isEqualTo("Article が見つかりません: id=abc-123");
            assertThat(ex.errorCode()).isEqualTo("ENTITY_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("BusinessRuleViolationException")
    class BusinessRuleViolationExceptionTest {

        @Test
        @DisplayName("メッセージとエラーコードを保持する")
        void holdsMessageAndErrorCode() {
            // Act
            final BusinessRuleViolationException ex = new BusinessRuleViolationException("非公開にできません");

            // Assert
            assertThat(ex.getMessage()).isEqualTo("非公開にできません");
            assertThat(ex.errorCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
        }

        @Test
        @DisplayName("起因例外を保持する")
        void holdsCause() {
            // Arrange
            final IllegalStateException cause = new IllegalStateException("root");

            // Act
            final BusinessRuleViolationException ex = new BusinessRuleViolationException("違反", cause);

            // Assert
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.errorCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
        }
    }
}
