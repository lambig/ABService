package com.abservice.presentation.rest.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * エンドポイントが実行するユースケースを指す
 *
 * <p>
 * エラー応答の契約はユースケースが持つ（{@code FailureContract}）。API 定義へ写すには、どのエンドポイントが
 * どのユースケースを実行するかが必要になるが、それはメソッドの本体にしかない情報で、注釈も戻り値型も持たない。
 * ここで指すことで、定義側は状態コードを知らずに失敗の契約へ辿れる。
 * </p>
 *
 * <p>
 * 指すのはユースケースであって状態コードではない。エンドポイントごとに 400・404・409 を書き並べる形（実質的に
 * {@code @APIResponse} の再発明）を避けるための宣言である。指した先を実際に呼んでいることは
 * {@code LayeredArchitectureTest} が双方向に突き合わせて守る。
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Executes {

    /**
     * このエンドポイントが実行するユースケース。
     *
     * @return アプリケーションサービスの型
     */
    Class<?> value();
}
