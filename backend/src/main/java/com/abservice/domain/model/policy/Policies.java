package com.abservice.domain.model.policy;

import static com.abservice.lib.Iterables.toList;
import static com.abservice.lib.Optionals.optionally;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.abservice.lib.ErrorResult;
import com.abservice.lib.Result;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * ポリシー／検証結果を組み合わせるためのユーティリティ。
 */
public final class Policies {

    private Policies() {
    }

    /**
     * 複数の検証結果を合成します。
     *
     * <p>
     * 失敗した検証のエラーをすべて集約します。エラーが1つも無ければ {@code constructor} を適用した成功を返します。
     * </p>
     *
     * @param validations
     *            合成対象の検証結果のリスト
     * @param constructor
     *            全検証成功時に生成する関数
     * @return 成功、または全エラーを集約した失敗
     * @param <R>
     *            生成される値の型
     */
    public static <R> Result<R> combine(List<Result<?>> validations, Supplier<? extends R> constructor) {
        return validations.stream()
                .flatMap(r -> r.errors().stream())
                .collect(optionally(toUnmodifiableList()))
                .filter(not(List::isEmpty))
                .<Result<R>>map(Result::failure)
                .orElseGet(() -> Result.success(constructor.get()));
    }

    /**
     * 複数フィールドの検証結果をまとめ、失敗を引数順に集約する<b>検証専用</b>の合成。
     *
     * <p>
     * 各フィールドの検証結果（{@code policy.verify(value, Function.identity())} 等）を受け取り、
     * すべて成功なら成功を、いずれか失敗ならすべてのエラーを<b>引数順に集約</b>した失敗を返します。
     * 値構築を伴わないため、{@link #combine(List, Supplier)} と異なり throwaway な生成関数を
     * 必要としません。成功時は検証通過を表す {@code true} を保持します（値としては利用しません）。
     * </p>
     *
     * <p>
     * 多フィールド値オブジェクトのコンパクトコンストラクタで、逐次 {@code verify().resolve()}
     * （先頭エラーで即時失敗）に代えて全フィールドのエラーを一度に集約する用途に使用します。
     * </p>
     *
     * @param validations
     *            各フィールドの検証結果（引数順にエラーを集約）
     * @return 全成功なら {@code Result.success(true)}、失敗があれば全エラーを集約した
     *         {@code Result.failure}
     */
    public static Result<Boolean> multiple(Result<?>... validations) {
        return Arrays.stream(validations)
                .flatMap(r -> r.errors().stream())
                .collect(optionally(toUnmodifiableList()))
                .filter(not(List::isEmpty))
                .<Result<Boolean>>map(Result::failure)
                .orElseGet(() -> Result.success(true));
    }

    /**
     * ネストした検証結果のエラーフィールドに親の名前を前置します。
     *
     * <p>
     * 成功はそのまま返し、失敗は各エラーの {@code field} を {@code parent + "." + field} に付け替えて再構築します。
     * </p>
     *
     * @param parent
     *            親フィールド名
     * @param result
     *            ネストした検証結果
     * @return フィールド名を付け替えた検証結果
     * @param <T>
     *            検証結果の値の型
     */
    public static <T> Result<T> nested(String parent, Result<T> result) {
        return result.errors().stream()
                .collect(optionally(toUnmodifiableList()))
                .filter(not(List::isEmpty))
                .map(
                        toList(
                                e -> new ErrorResult(
                                        parent + "." + e.field(),
                                        e.message(),
                                        e.code())))
                .<Result<T>>map(Result::failure)
                .orElse(result);
    }
}
