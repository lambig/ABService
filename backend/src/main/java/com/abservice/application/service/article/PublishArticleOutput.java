package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事公開コマンドの出力DTO
 *
 * @param articleId
 *            公開された記事のID
 * @param articleType
 *            記事種別（列挙子名）
 * @param title
 *            記事タイトル
 * @param publicFlag
 *            公開フラグ（公開成功時は常にtrue）
 */
public record PublishArticleOutput(
        String articleId,
        String articleType,
        String title,
        boolean publicFlag) implements CommandService.Output {
}
