package com.abservice.lib;

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
}
