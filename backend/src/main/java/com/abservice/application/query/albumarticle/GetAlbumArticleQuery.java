package com.abservice.application.query.albumarticle;

import com.abservice.application.query.QueryService;

/**
 * アルバム記事詳細照会クエリ
 *
 * @param albumId
 *            照会するアルバム記事のドメインID（対応するAlbum集約のIDと同じ）
 */
public record GetAlbumArticleQuery(String albumId) implements QueryService.Query {
}
