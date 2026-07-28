package com.abservice.domain.model.policy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

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

    /** 検証述語 */
    private final Predicate<? super T> predicate;
    /** 検証失敗時のエラー供給元 */
    private final Supplier<ErrorResult> errorSupplier;

    SinglePolicy(Predicate<? super T> predicate, Supplier<ErrorResult> errorSupplier) {
        this.predicate = predicate;
        this.errorSupplier = errorSupplier;
    }

    @Override
    @SuppressWarnings("NullAway") // predicate 合格後に constructor を適用するため value は検証済み（述語→非null を NullAway は追跡不可）
    public <R> Result<R> verify(@Nullable T value, Function<? super T, ? extends R> constructor) {
        return predicate.test(value)
                ? Result.success(constructor.apply(value))
                : Result.failure(errorSupplier.get());
    }
}
