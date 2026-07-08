package com.abservice.domain.model.policy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * ポリシー／検証結果を組み合わせるためのユーティリティ。
 */
public final class Policies {

    private Policies() {
    }

    /**
     * 複数の検証結果を合成します。
     *
     * <p>
     * 失敗した検証のエラーをすべて集約します。エラーが1つも無ければ {@code constructor} を適用した成功を返します。
     * </p>
     *
     * @param validations
     *            合成対象の検証結果のリスト
     * @param constructor
     *            全検証成功時に生成する関数
     * @return 成功、または全エラーを集約した失敗
     * @param <R>
     *            生成される値の型
     */
    public static <R> Result<R> combine(List<Result<?>> validations, Supplier<? extends R> constructor) {
        final List<ErrorResult> errors = validations.stream()
                .flatMap(r -> r instanceof Result.Failure<?> f ? f.errors().stream() : Stream.<ErrorResult>empty())
                .toList();
        return errors.isEmpty() ? Result.success(constructor.get()) : Result.failure(errors);
    }

    /**
     * ネストした検証結果のエラーフィールドに親の名前を前置します。
     *
     * <p>
     * 成功はそのまま返し、失敗は各エラーの {@code field} を {@code parent + "." + field} に付け替えて再構築します。
     * </p>
     *
     * @param parent
     *            親フィールド名
     * @param result
     *            ネストした検証結果
     * @return フィールド名を付け替えた検証結果
     * @param <T>
     *            検証結果の値の型
     */
    public static <T> Result<T> nested(String parent, Result<T> result) {
        return switch (result) {
            case Result.Success<T> success -> success;
            case Result.Failure<T> failure -> Result.failure(failure.errors().stream()
                    .map(e -> new ErrorResult(parent + "." + e.field(), e.message(), e.code())).toList());
        };
    }
}
