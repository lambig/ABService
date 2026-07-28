package com.abservice.domain.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Policy検証を経てAggregate/Entityを生成する経路（private factory / create / reconstruct）
 * であることを示す。
 *
 * <p>
 * 全項目を受け取るため引数が多いことはAlways Validパターンの帰結であり、個別の理由コメントを 要さない（Checkstyle
 * {@code SuppressionXpathFilter} が {@code ParameterNumber} を抑止する）。
 * </p>
 *
 * <p>
 * コンストラクタを呼び出す許可を示す {@link AggregateFactory}（{@code Stub.asXxx()} に付与、
 * ArchUnitで強制）とは対象メソッド・目的が異なる。
 * </p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface DomainFactory {
}
