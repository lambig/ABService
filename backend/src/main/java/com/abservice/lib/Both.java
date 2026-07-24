package com.abservice.lib;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 2値の組を表す汎用レコード。
 *
 * <p>
 * {@link Optionals#both} の戻り値等、呼び出し側専用のペア型を都度定義せずに済ませるために用いる。
 * </p>
 *
 * @param a
 *            1つ目の値
 * @param b
 *            2つ目の値
 * @param <A>
 *            1つ目の値の型
 * @param <B>
 *            2つ目の値の型
 */
public record Both<A, B>(A a, B b) {

    /**
     * BiPredicateを、組を受け取るPredicateへ変換する。
     *
     * <p>
     * static import して {@code .filter(Both.by(biPredicate))} のように、組を分解する
     * ラムダを書かずに用いる。
     * </p>
     *
     * @param predicate
     *            2値を受け取る述語
     * @return 組を受け取り述語を適用するPredicate
     * @param <A>
     *            1つ目の値の型
     * @param <B>
     *            2つ目の値の型
     */
    public static <A, B> Predicate<Both<A, B>> by(BiPredicate<? super A, ? super B> predicate) {
        return pair -> predicate.test(pair.a(), pair.b());
    }

    /**
     * BiFunctionを、組を受け取るFunctionへ変換する。
     *
     * <p>
     * static import して {@code .map(Both.to(biFunction))} のように、組を分解する ラムダを書かずに用いる。
     * </p>
     *
     * @param function
     *            2値を受け取る関数
     * @return 組を受け取り関数を適用するFunction
     * @param <A>
     *            1つ目の値の型
     * @param <B>
     *            2つ目の値の型
     * @param <R>
     *            関数の戻り値の型
     */
    public static <A, B, R> Function<Both<A, B>, R> to(BiFunction<? super A, ? super B, ? extends R> function) {
        return pair -> function.apply(pair.a(), pair.b());
    }
}
