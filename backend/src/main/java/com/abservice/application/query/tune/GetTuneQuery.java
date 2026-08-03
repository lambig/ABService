package com.abservice.application.query.tune;

import com.abservice.application.query.QueryService;

/**
 * チューン詳細照会クエリ
 *
 * @param tuneId
 *            照会するチューンのドメインID（UUIDv7形式の文字列）
 */
public record GetTuneQuery(String tuneId) implements QueryService.Query {
}
