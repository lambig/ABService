package com.abservice.lib;

import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;

/**
 * {@link Iterable} 用のユーティリティ。
 *
 * <p>
 * 反応系・変換系で頻出する「Iterable を写像して不変 List に集約する」定型を、 {@code .map(...)}
 * に渡せるカリー化関数として提供し、各所での {@code stream().map(...).toList()} 直書きを排する。
 * </p>
 */
public final class Iterables {

    private Iterables() {
    }

    /**
     * 要素写像を適用し不変 List に集約する関数を返す（カリー化）。
     *
     * <p>
     * static import して {@code .map(toList(Mapper::toDomain))} のように point-free で用いる。
     * </p>
     *
     * @param mapper
     *            要素の写像関数
     * @param <T>
     *            入力要素型
     * @param <R>
     *            出力要素型
     * @return Iterable を受け取り、写像結果の不変 List を返す関数
     */
    public static <T, R> Function<Iterable<T>, List<R>> toList(Function<? super T, ? extends R> mapper) {
        return source -> StreamSupport.stream(source.spliterator(), false)
                .<R>map(mapper)
                .toList();
    }
}
