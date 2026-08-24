package com.abservice.domain.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参照先集約を伴って構築され、規則を満たすときだけ遷移を実行する操作オブジェクトであることを示すマーカー。
 *
 * <p>
 * 参照先の状態に依存する遷移（{@link CrossAggregateTransition}）は、参照先を持たなければ判定できない。本アノテーションを
 * 付けたオブジェクトは構築時に参照先を受け取るため、「参照先を見ずに遷移する」経路が構築の時点で存在しない。
 * {@code com.abservice.architecture} の ArchUnit
 * ルールが、当該遷移がこのオブジェクトからのみ呼ばれることを強制する。
 * </p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CrossAggregateOperation {
}
