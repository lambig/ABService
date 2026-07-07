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
    @DisplayName("map")
    class MapTest {

        @Test
        @DisplayName("成功時は値を変換する")
        void mapShouldTransformSuccessValue() {
            // Arrange
            Result<Integer> result = Result.success(21);

            // Act
            Result<Integer> mapped = result.map(v -> v * 2);

            // Assert
            assertThat(mapped.resolve()).isEqualTo(42);
        }

        @Test
        @DisplayName("失敗時はエラーを引き継ぎ変換関数を実行しない")
        void mapShouldPropagateFailureWithoutApplyingMapper() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");
            Result<Integer> result = Result.failure(error);
            var applied = new boolean[]{false};

            // Act
            Result<Integer> mapped = result.map(v -> {
                applied[0] = true;
                return v * 2;
            });

            // Assert
            assertThat(applied[0]).isFalse();
            assertThat(mapped).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<Integer>) mapped).errors()).containsExactly(error);
        }
    }

    @Nested
    @DisplayName("flatMap")
    class FlatMapTest {

        @Test
        @DisplayName("成功時はResultを返す関数を適用し平坦化する")
        void flatMapShouldChainSuccess() {
            // Arrange
            Result<Integer> result = Result.success(10);

            // Act
            Result<String> mapped = result.flatMap(v -> Result.success("value=" + v));

            // Assert
            assertThat(mapped.resolve()).isEqualTo("value=10");
        }

        @Test
        @DisplayName("成功時に関数が失敗を返せばその失敗になる")
        void flatMapShouldReturnFailureFromMapper() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "invalid");
            Result<Integer> result = Result.success(10);

            // Act
            Result<String> mapped = result.flatMap(v -> Result.failure(error));

            // Assert
            assertThat(mapped).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) mapped).errors()).containsExactly(error);
        }

        @Test
        @DisplayName("失敗時はエラーを引き継ぎ変換関数を実行しない")
        void flatMapShouldPropagateFailureWithoutApplyingMapper() {
            // Arrange
            ErrorResult error = new ErrorResult("field", "message");
            Result<Integer> result = Result.failure(error);
            var applied = new boolean[]{false};

            // Act
            Result<String> mapped = result.flatMap(v -> {
                applied[0] = true;
                return Result.success("value=" + v);
            });

            // Assert
            assertThat(applied[0]).isFalse();
            assertThat(mapped).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) mapped).errors()).containsExactly(error);
        }
    }

    @Nested
    @DisplayName("zip")
    class ZipTest {

        @Test
        @DisplayName("2引数: 両方成功でcombinerを適用する")
        void zip2ShouldCombineBothSuccess() {
            // Arrange
            Result<String> a = Result.success("foo");
            Result<Integer> b = Result.success(3);

            // Act
            Result<String> combined = Result.zip(a, b, (s, n) -> s.repeat(n));

            // Assert
            assertThat(combined.resolve()).isEqualTo("foofoofoo");
        }

        @Test
        @DisplayName("2引数: 片方が失敗ならそのエラーを返す")
        void zip2ShouldReturnFailureWhenOneFails() {
            // Arrange
            ErrorResult error = new ErrorResult("b", "invalid");
            Result<String> a = Result.success("foo");
            Result<Integer> b = Result.failure(error);

            // Act
            Result<String> combined = Result.zip(a, b, (s, n) -> s.repeat(n));

            // Assert
            assertThat(combined).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) combined).errors()).containsExactly(error);
        }

        @Test
        @DisplayName("2引数: 両方失敗なら全エラーを集約する")
        void zip2ShouldAccumulateAllErrors() {
            // Arrange
            ErrorResult errorA = new ErrorResult("a", "invalid a");
            ErrorResult errorB = new ErrorResult("b", "invalid b");
            Result<String> a = Result.failure(errorA);
            Result<Integer> b = Result.failure(errorB);

            // Act
            Result<String> combined = Result.zip(a, b, (s, n) -> s.repeat(n));

            // Assert
            assertThat(combined).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) combined).errors()).containsExactly(errorA, errorB);
        }

        @Test
        @DisplayName("3引数: すべて成功でcombinerを適用する")
        void zip3ShouldCombineAllSuccess() {
            // Arrange
            Result<String> a = Result.success("a");
            Result<String> b = Result.success("b");
            Result<String> c = Result.success("c");

            // Act
            Result<String> combined = Result.zip(a, b, c, (x, y, z) -> x + y + z);

            // Assert
            assertThat(combined.resolve()).isEqualTo("abc");
        }

        @Test
        @DisplayName("3引数: 複数失敗なら全エラーを順序どおり集約する")
        void zip3ShouldAccumulateAllErrorsInOrder() {
            // Arrange
            ErrorResult errorA = new ErrorResult("a", "invalid a");
            ErrorResult errorC = new ErrorResult("c", "invalid c");
            Result<String> a = Result.failure(errorA);
            Result<String> b = Result.success("b");
            Result<String> c = Result.failure(errorC);

            // Act
            Result<String> combined = Result.zip(a, b, c, (x, y, z) -> x + y + z);

            // Assert
            assertThat(combined).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) combined).errors()).containsExactly(errorA, errorC);
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
