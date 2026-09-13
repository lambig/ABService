package com.abservice.presentation.rest.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 資源を作る操作であることを宣言する
 *
 * <p>
 * 作成に成功した応答は 201 と、作られた資源を指す {@code Location}、および作られた資源の表現を持つ（RFC 9110
 * §9.3.3・§15.3.2）。この3つは実装が {@code RestResponse} として組むが、smallrye-openapi は戻り値から
 * 状態コードもヘッダも読まないため、定義側には現れない。
 * </p>
 *
 * <p>
 * この注釈は状態コードを宣言するものではなく、「その操作が資源を作る」という一つの事実を表す。実装はそこから 201 と {@code Location}
 * を組み、定義は {@link CreatedResourceResponseFilter} が同じ事実から書く。状態コードを
 * 実装と定義の双方へ書き写さずに済ませるための唯一の出所である。
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CreatesResource {
}
