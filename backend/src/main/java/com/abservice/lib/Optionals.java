package com.abservice.lib;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * {@link Optional} 用のユーティリティ。
 *
 * <p>
 * 独立した2つのnull許容値を合成する定型（{@code Optional.ofNullable(a).flatMap(av -> Optional.ofNullable(b).map(...))}
 * の直書き）を排する。
 * </p>
 */
public final class Optionals {

    private Optionals() {
        // ユーティリティクラス
    }

    /**
     * 2つのnull許容値がともに非nullの場合のみ、組にして返す。
     *
     * <p>
     * static import して {@code both(a, b).filter(...).map(...)} のように用いる。
     * </p>
     *
     * @param a
     *            1つ目の値（nullable）
     * @param b
     *            2つ目の値（nullable）
     * @return 両方非nullなら{@link Both}、いずれかnullなら空のOptional
     * @param <A>
     *            1つ目の値の型
     * @param <B>
     *            2つ目の値の型
     */
    public static <A, B> Optional<Both<A, B>> both(@Nullable A a, @Nullable B b) {
        return Optional.ofNullable(a)
                .flatMap(
                        av -> Optional.ofNullable(b)
                                .map(bv -> new Both<>(av, bv)));
    }
}
