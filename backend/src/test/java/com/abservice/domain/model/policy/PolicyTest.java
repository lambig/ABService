package com.abservice.domain.model.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Policyバリデーション抽象")
class PolicyTest {

    private static Policy<String> nonBlank() {
        return Policy.of((String v) -> StringUtils.isNotBlank(v), () -> new ErrorResult("value", "必須です", "REQUIRED"));
    }

    private static Policy<String> maxLen(int max) {
        return Policy
                .of((String v) -> v == null || v.length() <= max, () -> new ErrorResult("value", "長すぎます", "TOO_LONG"));
    }

    @Nested
    @DisplayName("of / verify（単一ポリシー）")
    class SingleTest {

        @Test
        @DisplayName("述語が真なら生成関数を適用した成功を返す")
        void predicateTrueShouldSucceed() {
            final Result<Integer> result = nonBlank().verify("abc", String::length);

            assertThat(result.resolve()).isEqualTo(3);
        }

        @Test
        @DisplayName("述語が偽なら単一エラーの失敗を返す")
        void predicateFalseShouldFail() {
            final Result<Integer> result = nonBlank().verify("  ", String::length);

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<Integer>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("REQUIRED"));
        }
    }

    @Nested
    @DisplayName("all（合成ポリシー・エラー集約）")
    class CompositeTest {

        @Test
        @DisplayName("全ルール合格なら成功を返す")
        void allValidShouldSucceed() {
            final Result<String> result = Policy.all(nonBlank(), maxLen(10)).verify("hello", Function.identity());

            assertThat(result.resolve()).isEqualTo("hello");
        }

        @Test
        @DisplayName("複数ルール違反時はエラーを集約する")
        void multipleFailuresShouldBeAggregated() {
            final Result<String> result = Policy.all(nonBlank(), maxLen(3)).verify("     ", Function.identity());

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) result).errors()).extracting(ErrorResult::code)
                    .containsExactly("REQUIRED", "TOO_LONG");
        }

        @Test
        @DisplayName("リスト版allも同様に合成できる")
        void listOverloadShouldWork() {
            final Result<String> result = Policy.all(List.of(nonBlank(), maxLen(3)))
                    .verify("toolong", Function.identity());

            assertThat(((Result.Failure<String>) result).errors()).singleElement()
                    .satisfies(e -> assertThat(e.code()).isEqualTo("TOO_LONG"));
        }
    }

    @Nested
    @DisplayName("Policies.combine（検証結果の合成）")
    class CombineTest {

        @Test
        @DisplayName("全成功なら生成関数を適用した成功を返す")
        void allSuccessShouldSucceed() {
            final Result<String> result = Policies
                    .combine(List.of(Result.success("a"), Result.success(1)), () -> "built");

            assertThat(result.resolve()).isEqualTo("built");
        }

        @Test
        @DisplayName("失敗が混在する場合は全エラーを集約する")
        void mixedFailuresShouldBeAggregated() {
            final Result<String> result = Policies.combine(
                    List.of(
                            Result.failure(new ErrorResult("a", "msgA", "A")),
                            Result.success(1),
                            Result.failure(new ErrorResult("b", "msgB", "B"))),
                    () -> "built");

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) result).errors()).extracting(ErrorResult::code)
                    .containsExactly("A", "B");
        }
    }

    @Nested
    @DisplayName("Policies.nested（フィールド名の前置）")
    class NestedTest {

        @Test
        @DisplayName("成功はそのまま返す")
        void successShouldPassThrough() {
            final Result<String> result = Policies.nested("parent", Result.success("v"));

            assertThat(result.resolve()).isEqualTo("v");
        }

        @Test
        @DisplayName("失敗は各エラーのfieldに親名を前置する")
        void failureShouldPrefixField() {
            final Result<String> result = Policies
                    .nested("parent", Result.failure(new ErrorResult("child", "msg", "CODE")));

            assertThat(result).isInstanceOf(Result.Failure.class);
            assertThat(((Result.Failure<String>) result).errors()).singleElement().satisfies(e -> {
                assertThat(e.field()).isEqualTo("parent.child");
                assertThat(e.message()).isEqualTo("msg");
                assertThat(e.code()).isEqualTo("CODE");
            });
        }
    }
}
