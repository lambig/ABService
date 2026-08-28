package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事タグ削除の出力
 *
 * @param articleId
 *            記事のドメインID
 * @param tagId
 *            外したタグのドメインID
 */
public record RemoveArticleTagOutput(String articleId, String tagId) implements CommandService.Output {
}
