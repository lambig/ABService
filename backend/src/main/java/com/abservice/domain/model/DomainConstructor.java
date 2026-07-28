package com.abservice.domain.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aggregate/Entity の生コンストラクタ（フィールド代入のみ、検証なし）であることを示す。
 *
 * <p>
 * 全フィールドを受け取るため引数が多いことはAlways Validパターンの帰結であり、個別の理由コメント を要さない（Checkstyle
 * {@code SuppressionXpathFilter} が {@code ParameterNumber} を抑止する）。 呼び出せるのは
 * {@link AggregateFactory} が付与されたメソッドのみ（ArchUnitで強制）。
 * </p>
 *
 * <p>
 * 生コンストラクタ自身の役割を示す点で、コンストラクタを呼び出す許可を示す
 * {@link AggregateFactory}（{@code Stub.asXxx()} に付与）とは対象メソッド・目的が異なる。
 * </p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.CONSTRUCTOR)
public @interface DomainConstructor {
}
