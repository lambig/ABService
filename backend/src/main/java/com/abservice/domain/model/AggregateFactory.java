package com.abservice.domain.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aggregate/Entity の生コンストラクタを呼び出せる唯一のメソッドであることを示すマーカー。
 *
 * <p>
 * private な全項目コンストラクタは自身では検証を行わず、このアノテーションが付与された static factory メソッドが Policy
 * 検証を経てから呼び出すことを前提とする。 {@code com.abservice.architecture} の ArchUnit
 * ルールが、対象コンストラクタが このアノテーション付きメソッド以外から呼ばれていないことを強制する。
 * </p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface AggregateFactory {
}
