package com.abservice.application.query.album;

import com.abservice.application.query.QueryService;

/**
 * アルバムに対する操作の前提を問う照会クエリ
 *
 * @param albumId
 *            対象のアルバムのドメインID（UUIDv7形式の文字列）
 * @param operation
 *            前提を問う操作
 */
public record GetAlbumPreconditionsQuery(String albumId, AlbumOperation operation)
        implements
            QueryService.Query {
}
