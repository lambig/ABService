package com.abservice.application.query.publication;

import com.abservice.application.query.QueryService;

/**
 * @param generation
 *            保存済み世代（大小比較しない不透明なトークン）
 */
public record GetPublicDataGenerationResult(String generation) implements QueryService.Result {
}
