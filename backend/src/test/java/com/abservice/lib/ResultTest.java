package com.abservice.lib;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Result型のテスト")
class ResultTest {

    @Nested
    @DisplayName("Success")
    class SuccessTest {

        @Test
        @DisplayName("resolve()で値を取得できる")
        void resolveShouldReturnValue() {
            // Arrange
            Result<String> result = Result.success("test value");

            // Act
            String value = result.resolve();

            // Assert
            assertThat(value).isEqualTo("test value");
        }

        @Test
        @DisplayName("orElse()で値を取得できる")
        void orElseShouldReturnValue() {
            // Arrange
            Result<String> result = Result.success("test value");

            // Act
            String value = result.orElse("default");

            // Assert
            assertThat(value).isEqualTo("test value");
        }

        @Test
        @DisplayName("orElseGet()で値を取得できる")
        void orElseGetShouldReturnValue() {
            // Arrange
            Result<String> result = Result.success("test value");

            // Act
            String value = result.orElseGet(errors -> "default");

            // Assert
            assertThat(value).isEqualTo("test value");
        }

        @Test
        @DisplayName("orElseDo()で値を取得できる")
        void orElseDoShouldReturnValue() {
            // Arrange
            Result<String> result = Result.success("test value");

            // Act
            String value = result.orElseDo(errors -> {
            });

            // Assert
            assertThat(value).isEqualTo("test value");
        }
    }

    @Nested
    @DisplayName("Failure")
    class FailureTest {

        @Test
        @DisplayName("resolve()でデフォルト例外をスローする")
        void resolveShouldThrowDefaultException() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");
            Result<String> result = Result.failure(error);

            // Act & Assert
            assertThatThrownBy(result::resolve).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("field: message");
        }

        @Test
        @DisplayName("resolve()でカスタム例外をスローする")
        void resolveShouldThrowCustomException() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");
            Result<String> result = Result.failure(error);

            // Act & Assert
            assertThatThrownBy(() -> result.resolve(errors -> new IllegalArgumentException("custom")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("custom");
        }

        @Test
        @DisplayName("orElse()でデフォルト値を返す")
        void orElseShouldReturnDefault() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");
            Result<String> result = Result.failure(error);

            // Act
            String value = result.orElse("default");

            // Assert
            assertThat(value).isEqualTo("default");
        }

        @Test
        @DisplayName("orElseGet()でサプライヤーから値を取得する")
        void orElseGetShouldReturnSuppliedValue() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");
            Result<String> result = Result.failure(error);

            // Act
            String value = result.orElseGet(errors -> "supplied value");

            // Assert
            assertThat(value).isEqualTo("supplied value");
        }

        @Test
        @DisplayName("orElseGet()でエラーをサプライヤーに渡す")
        void orElseGetShouldPassErrorsToSupplier() {
            // Arrange
            ErrorResult error1 = new ErrorResult("field1", "message1");
            ErrorResult error2 = new ErrorResult("field2", "message2");
            Result<String> result = Result.failure(error1, error2);

            // Act
            String value = result.orElseGet(errors -> {
                assertThat(errors).hasSize(2);
                return "supplied value";
            });

            // Assert
            assertThat(value).isEqualTo("supplied value");
        }

        @Test
        @DisplayName("orElseDo()でアクションを実行してから例外をスローする")
        void orElseDoShouldExecuteActionAndThrow() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");
            Result<String> result = Result.failure(error);
            var executed = new boolean[]{false};

            // Act & Assert
            assertThatThrownBy(() -> result.orElseDo(errors -> {
                executed[0] = true;
            })).isInstanceOf(IllegalStateException.class);

            assertThat(executed[0]).isTrue();
        }
    }

    @Nested
    @DisplayName("ErrorResult")
    class ErrorResultTest {

        @Test
        @DisplayName("コードなしで生成できる")
        void constructorWithoutCode() {
            // Act
            ErrorResult error = new ErrorResult("field", "message");

            // Assert
            assertThat(error.field()).isEqualTo("field");
            assertThat(error.message()).isEqualTo("message");
            assertThat(error.code()).isNull();
        }

        @Test
        @DisplayName("コードありで生成できる")
        void constructorWithCode() {
            // Act
            ErrorResult error = new ErrorResult("field", "message", "ERR001");

            // Assert
            assertThat(error.field()).isEqualTo("field");
            assertThat(error.message()).isEqualTo("message");
            assertThat(error.code()).isEqualTo("ERR001");
        }

        @Test
        @DisplayName("toString()でコードなしのフォーマット")
        void toStringWithoutCode() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");

            // Act & Assert
            assertThat(error.toString()).isEqualTo("field: message");
        }

        @Test
        @DisplayName("toString()でコードありのフォーマット")
        void toStringWithCode() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message", "ERR001");

            // Act & Assert
            assertThat(error.toString()).isEqualTo("field: message (code: ERR001)");
        }
    }

    @Nested
    @DisplayName("ファクトリメソッド")
    class FactoryMethodTest {

        @Test
        @DisplayName("success()で成功を生成できる")
        void success() {
            // Act
            Result<String> result = Result.success("value");

            // Assert
            assertThat(result).isInstanceOf(Result.Success.class);
            assertThat(((Result.Success<String>) result).value()).isEqualTo("value");
        }

        @Test
        @DisplayName("failure()でリストから失敗を生成できる")
        void failureWithList() {
            // Arrange
            List<ErrorResult> errors = List.of(new ErrorResult("field1", "message1"),
                    new ErrorResult("field2", "message2"));

            // Act
            Result<String> result = Result.failure(errors);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) result).errors()).isEqualTo(errors);
        }

        @Test
        @DisplayName("failure()で可変長引数から失敗を生成できる")
        void failureWithVarargs() {
            // Arrange
            ErrorResult error1 = new ErrorResult("field1", "message1");
            ErrorResult error2 = new ErrorResult("field2", "message2");

            // Act
            Result<String> result = Result.failure(error1, error2);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) result).errors()).containsExactly(error1, error2);
        }

        @Test
        @DisplayName("failure()で空のエラーリストは例外をスローする")
        void failureWithEmptyListShouldThrow() {
            // Act & Assert
            assertThatThrownBy(() -> Result.failure(List.of())).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("errors must not be empty");
        }
    }
}
