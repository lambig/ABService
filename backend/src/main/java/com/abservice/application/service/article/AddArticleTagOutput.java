package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事タグ追加の出力
 *
 * @param articleId
 *            記事のドメインID
 * @param tagId
 *            付与したタグのドメインID
 * @param name
 *            タグ名
 */
public record AddArticleTagOutput(String articleId, String tagId, String name) implements CommandService.Output {
}
