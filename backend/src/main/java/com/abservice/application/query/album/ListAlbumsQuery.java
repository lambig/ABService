package com.abservice.application.query.album;

import com.abservice.application.query.Audience;
import com.abservice.application.query.QueryService;
import com.abservice.application.query.SortKeys;
import com.abservice.application.query.SortSpec;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * アルバム一覧照会のクエリ（ページネーション付き）
 *
 * <p>
 * {@code sort} / {@code direction} は未検証の外部入力をそのまま運ぶ。解決と検証は自身が
 * {@link AlbumSortKey} に照らして行う。
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
 *            タイトルでの絞り込み（nullable。未指定・空文字・空白のみなら絞り込まない）。部分一致で大文字小文字を
 *            問わない。記事編集画面が紐付け先を選ぶための検索であり、公開向けのエンドポイントは現時点でこの項目を 受け取らない
 * @param catalogNumber
 *            カタログナンバーでの絞り込み（nullable）。{@code title} と同じ扱いで、両方を指定した場合は
 *            積（AND）で絞り込む
 */
public record ListAlbumsQuery(
        int page,
        int size,
        Audience audience,
        @Nullable String sort,
        @Nullable String direction,
        @Nullable String title,
        @Nullable String catalogNumber) implements QueryService.Query {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /** 既存の補正契約を保ち、照会前に並び順を検証する。 */
    ValidatedQuery asValidated() {
        return new ValidatedQuery(
                clampPage(page),
                clampSize(size),
                audience,
                SortKeys.resolve(
                        AlbumSortKey.values(),
                        sort,
                        direction,
                        audience),
                keywordOrNull(title),
                keywordOrNull(catalogNumber));
    }

    record ValidatedQuery(
            int page,
            int size,
            Audience audience,
            SortSpec sort,
            @Nullable String title,
            @Nullable String catalogNumber) {
    }

    private static int clampPage(int page) {
        return Math.max(page, 0);
    }

    private static int clampSize(int size) {
        return size < 1
                ? DEFAULT_SIZE
                : Math.min(size, MAX_SIZE);
    }

    /**
     * 空文字・空白のみの検索語を未指定として扱う。
     *
     * <p>
     * 検索フォームの各値を常にクエリパラメータへ載せる実装では、未入力が空文字として届く。これをそのまま条件にすると {@code like '%%'}
     * が積に加わり、対象の列が null の行だけが結果から落ちる（SQL の {@code NULL LIKE '%%'} は真ではなく NULL
     * のため）。カタログナンバーは未付与がありうるので、絞り込んでいないつもりでカタログナンバーを 持たないアルバムが消えることになる。
     * </p>
     *
     * @param keyword
     *            クエリパラメータで指定された検索語（nullable）
     * @return 語として意味を持つ場合はその値、未指定・空文字・空白のみなら null
     */
    private static @Nullable String keywordOrNull(@Nullable String keyword) {
        return Optional.ofNullable(keyword)
                .filter(Predicate.not(String::isBlank))
                .orElse(null);
    }
}
