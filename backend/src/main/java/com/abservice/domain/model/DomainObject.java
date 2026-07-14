package com.abservice.domain.model;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * すべてのドメインオブジェクトの基底インターフェース
 *
 * <p>
 * ドメイン駆動設計において、ドメインオブジェクト（エンティティ、値オブジェクト等）が 共通して持つべき振る舞いを定義します。
 * </p>
 *
 * @param <T>
 *            実装クラスの型（CRTP: Curiously Recurring Template Pattern）
 */
public interface DomainObject<T extends DomainObject<T>> {
    /**
     * 等価性を検証する
     *
     * <p>
     * ドメインオブジェクトとしての等価性を判定します。 実装クラスでは、ビジネス上の同一性を表す属性に基づいて等価性を判定します。
     * </p>
     *
     * @param other
     *            比較対象のオブジェクト
     * @return 等価である場合はtrue、そうでない場合はfalse
     */
    boolean equivalentTo(T other);

    /**
     * 指定型へ絞り込む関数を返す（型を先に固定したカリー化）。 一致すればその型の値を、一致しなければ {@code null} を返すため、
     * {@code Optional.map(asType(SomeType.class))} の形で空へ落とせる。
     *
     * <p>
     * {@code equivalentTo} で上位型（sealed 兄弟型）引数を自身の具象型へ安全に絞り込む用途。
     * {@code instanceof}／キャストによる型判別をこの1メソッドに集約し、各実装からは締め出す。
     * </p>
     *
     * @param type
     *            絞り込む型
     * @return 一致すればその型の値、一致しなければ {@code null} を返す関数
     * @param <R>
     *            絞り込む型
     */
    static <R> Function<Object, @Nullable R> asType(Class<R> type) {
        return value -> type.isInstance(value)
                ? type.cast(value)
                : null;
    }
}
