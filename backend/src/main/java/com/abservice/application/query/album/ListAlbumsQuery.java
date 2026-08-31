package com.abservice.application.query.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;
import org.jspecify.annotations.Nullable;

/**
 * アルバム一覧照会のクエリ（ページネーション付き）
 *
 * <p>
 * {@code sort} / {@code direction} は未検証の外部入力をそのまま運ぶ。解決と検証は
 * {@link ListAlbumsService} が {@link AlbumSortKey} に照らして行う。
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
 * @param title
 *            タイトルでの絞り込み（nullable。未指定なら絞り込まない）。部分一致で大文字小文字を問わない。記事編集画面が
 *            紐付け先を選ぶための検索であり、公開向けのエンドポイントは現時点でこの項目を受け取らない
 * @param catalogNumber
 *            カタログナンバーでの絞り込み（nullable。未指定なら絞り込まない）。{@code title} と同じ扱いで、両方を
 *            指定した場合は積（AND）で絞り込む
 */
public record ListAlbumsQuery(
        int page,
        int size,
        Audience audience,
        @Nullable String sort,
        @Nullable String direction,
        @Nullable String title,
        @Nullable String catalogNumber) implements QueryService.Query {
}
