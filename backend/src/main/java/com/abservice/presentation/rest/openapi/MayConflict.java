package com.abservice.presentation.rest.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 業務ルール違反または競合（409）を返し得る操作であることを宣言する
 *
 * <p>
 * 409 を返す経路は実装の分岐の中にあり、注釈も戻り値型も持たない。HTTP メソッドから導くと、資源を書き換えない操作
 * （署名付きURLの払い出しなど）にまで 409 を宣言することになり、要求元は決して通らない枝を書く。返し得る操作を ここで名指しする。
 * </p>
 *
 * <p>
 * 対象は明示的な経路を持つ操作、すなわち業務ルール違反（{@code BusinessRuleViolationException}）を返すサービスを
 * 呼ぶか、編集開始時点の世代を条件に取る（{@code expectedRevision}）操作。基盤由来の楽観ロックはどの更新にも
 * 潜在するが、それを根拠に全更新へ広げると宣言の意味が失われるため含めない（`docs/DECISIONS.md` 32）。
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MayConflict {
}
