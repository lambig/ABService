package com.abservice.application.query.article;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;
import com.abservice.application.query.SortKeys;
import com.abservice.application.query.SortSpec;
import org.jspecify.annotations.Nullable;

/**
 * 記事一覧照会のクエリ（ページネーション付き）
 *
 * <p>
 * {@code sort} / {@code direction} は未検証の外部入力をそのまま運ぶ。解決と検証は自身が
 * {@link ArticleSortKey} に照らして行う。
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
 * @param publicFlag
 *            公開状態での絞り込み（nullable。未指定なら絞り込まない）。非公開化のカスケード対象は参照記事のうち公開中の
 *            ものに限るため、{@code albumId} と組み合わせてその集合を引く。公開向けのエンドポイントは公開中のものしか
 *            返さないためこの項目を受け取らない
 */
public record ListArticlesQuery(
        int page,
        int size,
        Audience audience,
        @Nullable String sort,
        @Nullable String direction,
        @Nullable String albumId,
        @Nullable Boolean publicFlag) implements QueryService.Query {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /** 既存の補正契約を保ち、照会前に並び順を検証する。 */
    ValidatedQuery asValidated() {
        return new ValidatedQuery(
                clampPage(page),
                clampSize(size),
                audience,
                SortKeys.resolve(
                        ArticleSortKey.values(),
                        sort,
                        direction,
                        audience),
                albumId,
                publicFlag);
    }

    record ValidatedQuery(
            int page,
            int size,
            Audience audience,
            SortSpec sort,
            @Nullable String albumId,
            @Nullable Boolean publicFlag) {
    }

    private static int clampPage(int page) {
        return Math.max(page, 0);
    }

    private static int clampSize(int size) {
        return size < 1
                ? DEFAULT_SIZE
                : Math.min(size, MAX_SIZE);
    }
}
