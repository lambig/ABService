package com.abservice.application.service;

import com.abservice.domain.model.policy.Policy;
import com.abservice.domain.model.policy.Policies;
import com.abservice.lib.Result;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * 入力の既存Result検証をPolicyへ接続する。エラーの順序・位置と想定外例外をそのまま保持する。
 *
 * @param <T>
 *            入力の型
 */
public final class InputPolicy<T> implements Policy<T> {
    private final Function<T, ? extends Result<?>> validation;

    /**
     * @param validation
     *            値の検証。I/O・時刻取得・集約の生成を含めない
     */
    public InputPolicy(Function<T, ? extends Result<?>> validation) {
        this.validation = validation;
    }

    @Override
    public <R> Result<R> verify(@Nullable T value, Function<? super T, ? extends R> constructor) {
        final var input = Objects.requireNonNull(value);
        return Policies.combine(List.of(validation.apply(input)), () -> constructor.apply(input));
    }
}
