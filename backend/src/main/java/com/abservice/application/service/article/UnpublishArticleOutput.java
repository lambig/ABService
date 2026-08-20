package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事非公開化コマンドの出力DTO
 *
 * @param articleId
 *            非公開化された記事のID
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publicFlag
 *            公開フラグ（非公開化成功時は常にfalse）
 */
public record UnpublishArticleOutput(
        String articleId,
        String articleType,
        String title,
        boolean publicFlag) implements CommandService.Output {
}
