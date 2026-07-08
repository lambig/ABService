package com.abservice.domain.model.policy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 単一の述語を評価するポリシー。
 *
 * <p>
 * 述語が真なら {@code constructor} を適用した成功、偽なら {@code errorSupplier}
 * が生成する単一エラーの失敗を返します。
 * </p>
 *
 * @param <T>
 *            検証対象の値の型
 */
final class SinglePolicy<T> implements Policy<T> {

    private final Predicate<? super T> predicate;
    private final Supplier<ErrorResult> errorSupplier;

    SinglePolicy(Predicate<? super T> predicate, Supplier<ErrorResult> errorSupplier) {
        this.predicate = predicate;
        this.errorSupplier = errorSupplier;
    }

    @Override
    public <R> Result<R> verify(T value, Function<? super T, ? extends R> constructor) {
        return predicate.test(value) ? Result.success(constructor.apply(value)) : Result.failure(errorSupplier.get());
    }
}
