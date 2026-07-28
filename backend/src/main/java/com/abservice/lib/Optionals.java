package com.abservice.lib;

import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
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

    /**
     * 任意のコレクタの集約結果を、常に非空の{@link Optional}に包んで返すコレクタへ変換する。
     *
     * <p>
     * 空/非空の判定は結果の型ごとに意味が異なる（例: {@code List} なら {@code isEmpty()}）ため 行わず、利用側の
     * {@code .filter(...)} 等に委ねる。{@code stream.collect(optionally(downstream))}
     * のように用い、{@code Optional.of(stream.collect(downstream))} の定型を排する。
     * </p>
     *
     * @param downstream
     *            後段で適用する集約
     * @return downstreamの集約結果をOptionalに包んで返すコレクタ
     * @param <T>
     *            要素の型
     * @param <A>
     *            downstreamの中間集約状態の型
     * @param <R>
     *            downstreamの集約結果の型
     */
    public static <T, A, R> Collector<T, A, Optional<R>> optionally(Collector<T, A, R> downstream) {
        return Collectors.collectingAndThen(downstream, Optional::of);
    }
}
