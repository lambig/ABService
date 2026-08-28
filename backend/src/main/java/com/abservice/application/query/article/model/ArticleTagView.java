package com.abservice.application.query.article.model;

/**
 * 記事タグの Read Model DTO
 *
 * <p>
 * タグは複数の記事が共有する語彙です。記事詳細に付いたタグとしても、管理画面が選択肢として引く一覧としても 同じ形で扱います。
 * </p>
 *
 * @param tagId
 *            タグID（ドメインID・UUIDv7形式の文字列）
 * @param name
 *            タグ名
 */
public record ArticleTagView(String tagId, String name) {
}
