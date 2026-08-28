package com.abservice.application.query.article;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;
import org.jspecify.annotations.Nullable;

/**
 * 記事一覧照会のクエリ（ページネーション付き）
 *
 * <p>
 * {@code sort} / {@code direction} は未検証の外部入力をそのまま運ぶ。解決と検証は
 * {@link ListArticlesService} が {@link ArticleSortKey} に照らして行う。
 * </p>
 *
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 * @param audience
 *            要求元（公開向けは公開中のみ、管理向けは下書きも対象）
 * @param sort
 *            並び順のキー（nullable。未指定なら登録の新しい順）
 * @param direction
 *            並び順の向き（nullable。未指定ならキーごとの既定）
 * @param albumId
 *            参照先アルバムでの絞り込み（nullable。未指定なら絞り込まない）。管理画面がカスケードの影響範囲を引くためのもので、
 *            公開向けのエンドポイントはこの項目を受け取らない
 */
public record ListArticlesQuery(
        int page,
        int size,
        Audience audience,
        @Nullable String sort,
        @Nullable String direction,
        @Nullable String albumId) implements QueryService.Query {
}
