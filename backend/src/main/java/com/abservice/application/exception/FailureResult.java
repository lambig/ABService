package com.abservice.application.exception;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 照会結果のバリアントが、境界では失敗として扱われることを表す
 *
 * <p>
 * 照会は「対象が無い」ことを例外ではなく正常な結果の一種として返す（{@code GetAlbumResult.NotFound} など）。
 * 型がすでにその可能性を持っているため、ユースケース側で {@link FailureContract} として重ねて宣言すると、同じ事実を
 * 二度書くことになる。API 定義へ載せる失敗は、結果型の並びから導く。
 * </p>
 *
 * <p>
 * 対になるのは {@link FailureContract} で、あちらはユースケース自身が失敗を発生させる場合（入力の検証など）に使う。 どちらも語彙は
 * {@link Failure} で、HTTP を含まない。
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FailureResult {

    /**
     * このバリアントが境界で対応づけられる失敗。
     *
     * @return 失敗の種類
     */
    Failure value();
}
