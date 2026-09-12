package com.abservice.presentation.rest;

import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * 作成に成功した応答を組む
 *
 * <p>
 * 資源を作った POST は 201 と、作られた資源を指す {@code Location}、および作られた資源の表現を返す（RFC 9110
 * §9.3.3・§15.3.2）。3つを組む形をここに集約し、資源ごとに違うのは位置の綴りだけにする。作る操作であることの宣言は
 * {@code CreatesResource} が持つ。
 * </p>
 *
 * <p>
 * 位置は同一オリジンの相対参照で表す。RFC 9110 が {@code Location} に許すのは URI 参照であり、絶対 URI を
 * 組むと配信の経路（CloudFront）より内側のホストを応答へ載せることになる。要求元は同じオリジンへ戻るため、 絶対化して得るものがない。
 * </p>
 */
public final class CreatedResponses {

    private CreatedResponses() {
    }

    /**
     * 作られた資源の位置と表現から、作成に成功した応答を組みます。
     *
     * @param <T>
     *            作られた資源の表現の型
     * @param location
     *            作られた資源を指す相対参照
     * @param body
     *            作られた資源の表現
     * @return 201 と {@code Location} と表現を持つ応答
     */
    public static <T> RestResponse<T> at(String location, T body) {
        /*
         * RELATIVE-LOCATION: ResponseBuilder.location(URI) は JAX-RS の規定によりベース URI へ
         * 解決されるため、相対参照を渡しても絶対 URI になる。同一オリジンの相対参照のまま返すには、 ヘッダへ直接与えるしかない。
         */
        return RestResponse.ResponseBuilder.<T>create(RestResponse.StatusCode.CREATED)
                .entity(body)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
