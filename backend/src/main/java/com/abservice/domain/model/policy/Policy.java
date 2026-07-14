package com.abservice.domain.model.policy;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * 値オブジェクトなどの生成前検証を表現するポリシー抽象。
 *
 * <p>
 * ポリシーは値 {@code T} を検証し、成功時のみ {@code constructor} を適用して {@code R} を生成した
 * {@link Result} を返します。検証と生成を一体で扱うことで、未検証値からの生成を防ぎます。
 * </p>
 *
 * <p>
 * 単一の述語から {@link #of(Predicate, Supplier)} で、複数ポリシーの合成（エラー集約）から
 * {@link #all(Policy[])} / {@link #all(List)} で組み立てます。直前の成功値に依存する逐次検証には
 * {@link Result#flatMap(Function)} を併用します。
 * </p>
 *
 * @param <T>
 *            検証対象の値の型
 */
public interface Policy<T> {

    /**
     * 値を検証し、成功時のみ {@code constructor} を適用した結果を返します。
     *
     * @param value
     *            検証対象の値
     * @param constructor
     *            検証成功時に成功値へ適用する生成関数
     * @return 成功時は生成値の {@code Success}、失敗時は検証エラーの {@code Failure}
     * @param <R>
     *            生成される値の型
     */
    <R> Result<R> verify(@Nullable T value, Function<? super T, ? extends R> constructor);

    /**
     * 単一の述語からポリシーを生成します。
     *
     * @param predicate
     *            検証述語（true で合格）
     * @param errorSupplier
     *            不合格時のエラー生成関数
     * @return 単一述語ポリシー
     * @param <T>
     *            検証対象の値の型
     */
    static <T> Policy<T> of(Predicate<? super T> predicate, Supplier<ErrorResult> errorSupplier) {
        return new SinglePolicy<>(predicate, errorSupplier);
    }

    /**
     * 複数のポリシーを合成し、全ルールのエラーを集約するポリシーを生成します。
     *
     * @param rules
     *            合成対象のポリシー
     * @return 合成ポリシー
     * @param <T>
     *            検証対象の値の型
     */
    @SafeVarargs
    static <T> Policy<T> all(Policy<T>... rules) {
        return new CompositePolicy<>(List.of(rules));
    }

    /**
     * 複数のポリシーを合成し、全ルールのエラーを集約するポリシーを生成します。
     *
     * @param rules
     *            合成対象のポリシーのリスト
     * @return 合成ポリシー
     * @param <T>
     *            検証対象の値の型
     */
    static <T> Policy<T> all(List<Policy<T>> rules) {
        return new CompositePolicy<>(rules);
    }
}
