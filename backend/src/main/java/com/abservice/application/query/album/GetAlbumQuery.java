package com.abservice.application.query.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;

/**
 * アルバム詳細照会クエリ
 *
 * @param albumId
 *            照会するアルバムのドメインID（UUIDv7形式の文字列）
 * @param audience
 *            要求元（公開向けは公開中のみ、管理向けは下書きも対象）
 */
public record GetAlbumQuery(String albumId, Audience audience) implements QueryService.Query {
}
