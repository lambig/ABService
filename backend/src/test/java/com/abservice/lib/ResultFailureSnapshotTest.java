package com.abservice.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultFailureSnapshotTest {

    @Test
    @DisplayName("入力リストを空にしても失敗が成功へ変わらず、エラーの順序と重複を保つ")
    void inputListMutationCannotTurnFailureIntoSuccess() {
        final var first = new ErrorResult(
                "first",
                "required",
                "REQUIRED");
        final var second = new ErrorResult(
                "second",
                "invalid",
                "INVALID");
        final var errors = new ArrayList<>(List.of(
                first,
                second,
                first));
        final Result<String> failure = Result.failure(errors);

        errors.clear(); // NOPMD - 入力の変更でFailureの不変条件が壊れないことを再現する。

        assertThat(Result.all(List.of(failure, Result.success("valid"))).errors())
                .containsExactly(
                        first,
                        second,
                        first);
        assertThat(failure.errors()).containsExactly(
                first,
                second,
                first);
    }

    @Test
    @DisplayName("可変長引数の配列を書き換えても生成時のエラーを保つ")
    void inputArrayMutationCannotChangeFailure() {
        final var original = new ErrorResult("value", "required");
        final var errors = new ErrorResult[]{original};
        final Result<String> failure = Result.failure(errors);

        errors[0] = new ErrorResult("other", "replacement");

        assertThat(failure.errors()).containsExactly(original);
    }

    @Test
    @DisplayName("直接生成したFailureでも返されたリストから非空条件を壊せない")
    void exposedErrorsCannotBeCleared() {
        final var error = new ErrorResult("value", "required");
        final var failure = new Result.Failure<String>(new ArrayList<>(List.of(error)));

        assertThatThrownBy(() -> failure.errors().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(failure.errors()).containsExactly(error);
    }
}
