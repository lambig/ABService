package com.abservice.domain.model;

/**
 * すべてのドメインオブジェクトの基底インターフェース
 *
 * <p>ドメイン駆動設計において、ドメインオブジェクト（エンティティ、値オブジェクト等）が
 * 共通して持つべき振る舞いを定義します。</p>
 *
 * @param <T> 実装クラスの型（CRTP: Curiously Recurring Template Pattern）
 */
public interface DomainObject<T extends DomainObject<T>> {
    /**
     * 等価性を検証する
     *
     * <p>ドメインオブジェクトとしての等価性を判定します。
     * 実装クラスでは、ビジネス上の同一性を表す属性に基づいて等価性を判定します。</p>
     *
     * @param other 比較対象のオブジェクト
     * @return 等価である場合はtrue、そうでない場合はfalse
     */
    boolean equivalentTo(T other);
}
