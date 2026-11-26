package com.abservice.application.query.tune;

import com.abservice.application.query.QueryService;
import com.abservice.application.query.SortKeys;
import com.abservice.application.query.SortSpec;
import com.abservice.application.query.Audience;
import org.jspecify.annotations.Nullable;

/**
 * チューン一覧照会のクエリ（ページネーション付き）
 *
 * <p>
 * チューン一覧は認証必須のマスタ系照会のため要求元は管理向けのみで、{@code audience} は持たない。{@code sort} /
 * {@code direction} は未検証の外部入力をそのまま運び、解決と検証は自身が {@link TuneSortKey} に照らして行う。
 * </p>
 *
 * @param page
 *            ページ番号（0始まり）
 * @param size
 *            1ページの件数
 * @param sort
 *            並び順のキー（nullable。未指定なら登録の新しい順）
 * @param direction
 *            並び順の向き（nullable。未指定ならキーごとの既定）
 */
public record ListTunesQuery(
        int page,
        int size,
        @Nullable String sort,
        @Nullable String direction) implements QueryService.Query {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /** 既存の補正契約を保ち、照会前に並び順を検証する。 */
    ValidatedQuery asValidated() {
        return new ValidatedQuery(
                clampPage(page),
                clampSize(size),
                SortKeys.resolve(
                        TuneSortKey.values(),
                        sort,
                        direction,
                        Audience.ADMIN));
    }

    record ValidatedQuery(
            int page,
            int size,
            SortSpec sort) {
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
