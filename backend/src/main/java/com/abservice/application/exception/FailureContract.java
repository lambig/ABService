package com.abservice.application.exception;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ユースケースが返し得る失敗を宣言する
 *
 * <p>
 * 失敗はユースケースの能力であり、経路（REST）の形から導けるものではない。要求本体や問合せ文字列を持たない操作でも 経路の識別子を検証すれば
 * {@link Failure#VALIDATION} を返し、経路で対象を指しても存在しないことを成功として 扱うなら
 * {@link Failure#NOT_FOUND} は返さない。したがって宣言の場所は、その判断を持つユースケース自身に置く。
 * </p>
 *
 * <p>
 * API 定義のエラー応答は、エンドポイントが呼ぶユースケースのこの宣言から組む（{@code presentation.rest.openapi}）。
 * エンドポイントごとに状態コードを列挙しないための唯一の出所である。宣言と実装の対応は {@code LayeredArchitectureTest}
 * が、失敗を生む入口（値オブジェクトの生成・存在確認のドメインサービス・ 競合を検出する書き込み）への依存と突き合わせて守る。
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FailureContract {

    /**
     * このユースケースが返し得る失敗。
     *
     * @return 失敗の種類
     */
    Failure[] value();
}
