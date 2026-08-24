package com.abservice.domain.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 集約をまたぐ規則の判定を経てからでなければ呼べない状態遷移であることを示すマーカー。
 *
 * <p>
 * 自集約の情報だけでは可否を判定できない遷移（参照先集約の状態に依存するもの）は、参照先を引いて規則を適用する
 * ドメインサービスが仲介する。遷移メソッド自身は判定を持てないため、呼び出し側の順序に依存せず守れるよう、
 * {@code com.abservice.architecture} の ArchUnit ルールが「ドメインサービスからのみ呼ばれる」ことを強制する。
 * </p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CrossAggregateTransition {
}
