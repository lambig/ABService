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
            final Result<String> result = Result.success("test value");

            // Act
            final String value = result.resolve();

            // Assert
            assertThat(value).isEqualTo("test value");
        }

        @Test
        @DisplayName("orElse()で値を取得できる")
        void orElseShouldReturnValue() {
            // Arrange
            final Result<String> result = Result.success("test value");

            // Act
            final String value = result.orElse("default");

            // Assert
            assertThat(value).isEqualTo("test value");
        }

        @Test
        @DisplayName("orElseGet()で値を取得できる")
        void orElseGetShouldReturnValue() {
            // Arrange
            final Result<String> result = Result.success("test value");

            // Act
            final String value = result.orElseGet(errors -> "default");

            // Assert
            assertThat(value).isEqualTo("test value");
        }

        @Test
        @DisplayName("orElseDo()で値を取得できる")
        void orElseDoShouldReturnValue() {
            // Arrange
            final Result<String> result = Result.success("test value");

            // Act
            final String value = result.orElseDo(errors -> {
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
            final ErrorResult error = new ErrorResult("field", "message");
            final Result<String> result = Result.failure(error);

            // Act & Assert
            assertThatThrownBy(result::resolve).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("field: message");
        }

        @Test
        @DisplayName("resolve()でカスタム例外をスローする")
        void resolveShouldThrowCustomException() {
            // Arrange
            final ErrorResult error = new ErrorResult("field", "message");
            final Result<String> result = Result.failure(error);

            // Act & Assert
            assertThatThrownBy(() -> result.resolve(errors -> new IllegalArgumentException("custom")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("custom");
        }

        @Test
        @DisplayName("orElse()でデフォルト値を返す")
        void orElseShouldReturnDefault() {
            // Arrange
            final ErrorResult error = new ErrorResult("field", "message");
            final Result<String> result = Result.failure(error);

            // Act
            final String value = result.orElse("default");

            // Assert
            assertThat(value).isEqualTo("default");
        }

        @Test
        @DisplayName("orElseGet()でサプライヤーから値を取得する")
        void orElseGetShouldReturnSuppliedValue() {
            // Arrange
            final ErrorResult error = new ErrorResult("field", "message");
            final Result<String> result = Result.failure(error);

            // Act
            final String value = result.orElseGet(errors -> "supplied value");

            // Assert
            assertThat(value).isEqualTo("supplied value");
        }

        @Test
        @DisplayName("orElseGet()でエラーをサプライヤーに渡す")
        void orElseGetShouldPassErrorsToSupplier() {
            // Arrange
            final ErrorResult error1 = new ErrorResult("field1", "message1");
            final ErrorResult error2 = new ErrorResult("field2", "message2");
            final Result<String> result = Result.failure(error1, error2);

            // Act
            final String value = result.orElseGet(errors -> {
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
            final ErrorResult error = new ErrorResult("field", "message");
            final Result<String> result = Result.failure(error);
            final var executed = new boolean[]{false};

            // Act & Assert
            assertThatThrownBy(() -> result.orElseDo(errors -> {
                assertThat(errors).containsExactly(error);
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
            final ErrorResult error = new ErrorResult("field", "message");

            // Assert
            assertThat(error.field()).isEqualTo("field");
            assertThat(error.message()).isEqualTo("message");
            assertThat(error.code()).isNull();
        }

        @Test
        @DisplayName("コードありで生成できる")
        void constructorWithCode() {
            // Act
            final ErrorResult error = new ErrorResult(
                    "field",
                    "message",
                    "ERR001");

            // Assert
            assertThat(error.field()).isEqualTo("field");
            assertThat(error.message()).isEqualTo("message");
            assertThat(error.code()).isEqualTo("ERR001");
        }

        @Test
        @DisplayName("toString()でコードなしのフォーマット")
        void toStringWithoutCode() {
            // Arrange
            final ErrorResult error = new ErrorResult("field", "message");

            // Act & Assert
            assertThat(error.toString()).isEqualTo("field: message");
        }

        @Test
        @DisplayName("toString()でコードありのフォーマット")
        void toStringWithCode() {
            // Arrange
            final ErrorResult error = new ErrorResult(
                    "field",
                    "message",
                    "ERR001");

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
            final Result<Integer> result = Result.success(21);

            // Act
            final Result<Integer> mapped = result.map(v -> v * 2);

            // Assert
            assertThat(mapped.resolve()).isEqualTo(42);
        }

        @Test
        @DisplayName("失敗時はエラーを引き継ぎ変換関数を実行しない")
        void mapShouldPropagateFailureWithoutApplyingMapper() {
            // Arrange
            final ErrorResult error = new ErrorResult("field", "message");
            final Result<Integer> result = Result.failure(error);
            final var applied = new boolean[]{false};

            // Act
            final Result<Integer> mapped = result.map(v -> {
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
            final Result<Integer> result = Result.success(10);

            // Act
            final Result<String> mapped = result.flatMap(v -> Result.success("value=" + v));

            // Assert
            assertThat(mapped.resolve()).isEqualTo("value=10");
        }

        @Test
        @DisplayName("成功時に関数が失敗を返せばその失敗になる")
        void flatMapShouldReturnFailureFromMapper() {
            // Arrange
            final Result<Integer> result = Result.success(10);

            // Act
            final Result<String> mapped = result.flatMap(
                    v -> Result.failure(new ErrorResult("field", "invalid value=" + v)));

            // Assert
            assertThat(mapped).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) mapped).errors())
                    .containsExactly(new ErrorResult("field", "invalid value=10"));
        }

        @Test
        @DisplayName("失敗時はエラーを引き継ぎ変換関数を実行しない")
        void flatMapShouldPropagateFailureWithoutApplyingMapper() {
            // Arrange
            final ErrorResult error = new ErrorResult("field", "message");
            final Result<Integer> result = Result.failure(error);
            final var applied = new boolean[]{false};

            // Act
            final Result<String> mapped = result.flatMap(v -> {
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
            final Result<String> a = Result.success("foo");
            final Result<Integer> b = Result.success(3);

            // Act
            final Result<String> combined = Result.zip(
                    a,
                    b,
                    (s, n) -> s.repeat(n));

            // Assert
            assertThat(combined.resolve()).isEqualTo("foofoofoo");
        }

        @Test
        @DisplayName("2引数: 片方が失敗ならそのエラーを返す")
        void zip2ShouldReturnFailureWhenOneFails() {
            // Arrange
            final ErrorResult error = new ErrorResult("b", "invalid");
            final Result<String> a = Result.success("foo");
            final Result<Integer> b = Result.failure(error);

            // Act
            final Result<String> combined = Result.zip(
                    a,
                    b,
                    (s, n) -> s.repeat(n));

            // Assert
            assertThat(combined).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) combined).errors()).containsExactly(error);
        }

        @Test
        @DisplayName("2引数: 両方失敗なら全エラーを集約する")
        void zip2ShouldAccumulateAllErrors() {
            // Arrange
            final ErrorResult errorA = new ErrorResult("a", "invalid a");
            final ErrorResult errorB = new ErrorResult("b", "invalid b");
            final Result<String> a = Result.failure(errorA);
            final Result<Integer> b = Result.failure(errorB);

            // Act
            final Result<String> combined = Result.zip(
                    a,
                    b,
                    (s, n) -> s.repeat(n));

            // Assert
            assertThat(combined).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) combined).errors()).containsExactly(errorA, errorB);
        }

        @Test
        @DisplayName("3引数: すべて成功でcombinerを適用する")
        void zip3ShouldCombineAllSuccess() {
            // Arrange
            final Result<String> a = Result.success("a");
            final Result<String> b = Result.success("b");
            final Result<String> c = Result.success("c");

            // Act
            final Result<String> combined = Result.zip(
                    a,
                    b,
                    c,
                    (x, y, z) -> x + y + z);

            // Assert
            assertThat(combined.resolve()).isEqualTo("abc");
        }

        @Test
        @DisplayName("3引数: 複数失敗なら全エラーを順序どおり集約する")
        void zip3ShouldAccumulateAllErrorsInOrder() {
            // Arrange
            final ErrorResult errorA = new ErrorResult("a", "invalid a");
            final ErrorResult errorC = new ErrorResult("c", "invalid c");
            final Result<String> a = Result.failure(errorA);
            final Result<String> b = Result.success("b");
            final Result<String> c = Result.failure(errorC);

            // Act
            final Result<String> combined = Result.zip(
                    a,
                    b,
                    c,
                    (x, y, z) -> x + y + z);

            // Assert
            assertThat(combined).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) combined).errors()).containsExactly(errorA, errorC);
        }

        @Test
        @DisplayName("4引数: すべて成功でcombinerを適用する")
        void zip4ShouldCombineAllSuccess() {
            // Arrange
            final Result<String> a = Result.success("a");
            final Result<String> b = Result.success("b");
            final Result<String> c = Result.success("c");
            final Result<String> d = Result.success("d");

            // Act
            final Result<String> combined = Result.zip(
                    a,
                    b,
                    c,
                    d,
                    (w, x, y, z) -> w + x + y + z);

            // Assert
            assertThat(combined.resolve()).isEqualTo("abcd");
        }

        @Test
        @DisplayName("4引数: 複数失敗なら全エラーを順序どおり集約する")
        void zip4ShouldAccumulateAllErrorsInOrder() {
            // Arrange
            final ErrorResult errorB = new ErrorResult("b", "invalid b");
            final ErrorResult errorD = new ErrorResult("d", "invalid d");
            final Result<String> a = Result.success("a");
            final Result<String> b = Result.failure(errorB);
            final Result<String> c = Result.success("c");
            final Result<String> d = Result.failure(errorD);

            // Act
            final Result<String> combined = Result.zip(
                    a,
                    b,
                    c,
                    d,
                    (w, x, y, z) -> w + x + y + z);

            // Assert
            assertThat(combined).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) combined).errors()).containsExactly(errorB, errorD);
        }
    }

    @Nested
    @DisplayName("ファクトリメソッド")
    class FactoryMethodTest {

        @Test
        @DisplayName("success()で成功を生成できる")
        void success() {
            // Act
            final Result<String> result = Result.success("value");

            // Assert
            assertThat(result).isInstanceOf(Result.Success.class);
            assertThat(((Result.Success<String>) result).value()).isEqualTo("value");
        }

        @Test
        @DisplayName("failure()でリストから失敗を生成できる")
        void failureWithList() {
            // Arrange
            final List<ErrorResult> errors = List
                    .of(new ErrorResult("field1", "message1"), new ErrorResult("field2", "message2"));

            // Act
            final Result<String> result = Result.failure(errors);

            // Assert
            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) result).errors()).isEqualTo(errors);
        }

        @Test
        @DisplayName("failure()で可変長引数から失敗を生成できる")
        void failureWithVarargs() {
            // Arrange
            final ErrorResult error1 = new ErrorResult("field1", "message1");
            final ErrorResult error2 = new ErrorResult("field2", "message2");

            // Act
            final Result<String> result = Result.failure(error1, error2);

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
