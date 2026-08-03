package com.abservice.application.query.album;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.album.model.AlbumView;

/**
 * アルバム詳細照会の結果
 *
 * <p>
 * 「未存在」を例外ではなく正常な結果の一種として型で表現します（sealed）。presentation 層は各バリアントで switch
 * し、{@link Found} を 200、{@link NotFound} を 404 に対応づけます。
 * </p>
 */
public sealed interface GetAlbumResult extends QueryService.Result
        permits GetAlbumResult.Found, GetAlbumResult.NotFound {

    /**
     * アルバムが見つかった結果（→ 200）
     *
     * @param album
     *            アルバムの Read Model
     */
    record Found(AlbumView album) implements GetAlbumResult {
    }

    /**
     * アルバムが見つからなかった結果（→ 404）
     */
    record NotFound() implements GetAlbumResult {
    }
}
