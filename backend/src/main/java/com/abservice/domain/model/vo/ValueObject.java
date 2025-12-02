package com.abservice.domain.model.vo;

import com.abservice.domain.model.DomainObject;

/**
 * 値オブジェクトのインターフェース
 *
 * <p>
 * ドメイン駆動設計における値オブジェクト（Value Object）を表します。 値オブジェクトは以下の特性を持ちます：
 * </p>
 * <ul>
 * <li>不変性（Immutability）：一度生成されたら状態を変更できない</li>
 * <li>等価性（Equality）：属性の値が同じであれば等価とみなされる</li>
 * <li>副作用のない振る舞い（Side-Effect Free Behavior）：メソッド実行が状態を変更しない</li>
 * </ul>
 *
 * <h2>等価性の評価</h2>
 * <p>
 * 値オブジェクトの等価性は以下のように評価されます：
 * </p>
 * <ul>
 * <li>DomainObject型のフィールド：equivalentTo()メソッドで等価性を評価</li>
 * <li>その他のフィールド：==演算子またはequals()で同値性を評価</li>
 * <li>すべてのフィールドが上記の条件を満たす場合に等価とみなされる</li>
 * </ul>
 *
 * <h2>実装上の注意</h2>
 * <ul>
 * <li>equivalentTo()メソッドは各実装クラスで明示的に実装する必要があります</li>
 * <li>Java Recordsの使用を推奨します（Java 14+）</li>
 * <li>不変性を保証するため、すべてのフィールドはfinalにしてください</li>
 * </ul>
 *
 * @param <T>
 *            実装クラスの型（CRTP: Curiously Recurring Template Pattern）
 */
public interface ValueObject<T extends ValueObject<T>> extends DomainObject<T> {
}
