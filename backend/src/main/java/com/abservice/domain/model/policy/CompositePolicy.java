package com.abservice.domain.model.policy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

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

    /** 合成対象のルール一覧 */
    private final List<Policy<T>> rules;

    CompositePolicy(List<Policy<T>> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    @SuppressWarnings("NullAway") // 全ルール合格後に constructor を適用するため value は検証済み（述語→非null を NullAway は追跡不可）
    public <R> Result<R> verify(@Nullable T value, Function<? super T, ? extends R> constructor) {
        final List<ErrorResult> errors = rules.stream().map(rule -> rule.verify(value, Function.identity()))
                .flatMap(r -> r.errors().stream()).toList();
        return errors.isEmpty()
                ? Result.success(constructor.apply(value))
                : Result.failure(errors);
    }
}
