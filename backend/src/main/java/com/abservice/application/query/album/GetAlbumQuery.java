package com.abservice.application.query.album;

import com.abservice.application.query.QueryService;

/**
 * アルバム詳細照会クエリ
 *
 * @param albumId
 *            照会するアルバムのドメインID（UUIDv7形式の文字列）
 */
public record GetAlbumQuery(String albumId) implements QueryService.Query {
}
