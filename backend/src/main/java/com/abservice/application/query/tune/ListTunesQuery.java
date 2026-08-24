package com.abservice.application.query.tune;

import com.abservice.application.query.QueryService;
import org.jspecify.annotations.Nullable;

/**
 * チューン一覧照会のクエリ（ページネーション付き）
 *
 * <p>
 * チューン一覧は認証必須のマスタ系照会のため要求元は管理向けのみで、{@code audience} は持たない。{@code sort} /
 * {@code direction} は未検証の外部入力をそのまま運び、解決と検証は {@link ListTunesService} が
 * {@link TuneSortKey} に照らして行う。
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
}
