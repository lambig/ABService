package com.abservice.domain.model.policy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 複数のポリシーを合成し、エラーを集約するポリシー。
 *
 * <p>
 * 全ルールを走らせ、失敗したルールのエラーをすべて集約します。エラーが1つも無ければ {@code constructor} を適用した成功を返します。
 * </p>
 *
 * @param <T>
 *            検証対象の値の型
 */
final class CompositePolicy<T> implements Policy<T> {

    private final List<Policy<T>> rules;

    CompositePolicy(List<Policy<T>> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public <R> Result<R> verify(T value, Function<? super T, ? extends R> constructor) {
        final List<ErrorResult> errors = rules.stream().map(rule -> rule.verify(value, Function.identity()))
                .flatMap(r -> r instanceof Result.Failure<T> f
                        ? f.errors().stream()
                        : Stream.<ErrorResult>empty())
                .toList();
        return errors.isEmpty()
                ? Result.success(constructor.apply(value))
                : Result.failure(errors);
    }
}
