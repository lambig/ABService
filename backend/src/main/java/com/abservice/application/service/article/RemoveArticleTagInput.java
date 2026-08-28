package com.abservice.application.service.article;

import com.abservice.application.service.CommandService;

/**
 * 記事タグ削除の入力
 *
 * @param articleId
 *            対象記事のドメインID
 * @param tagId
 *            外すタグのドメインID
 */
public record RemoveArticleTagInput(String articleId, String tagId) implements CommandService.Input {
}
