/** 記事一覧の1ページあたりの件数（#197） */
export const ARTICLES_PER_PAGE = 20;

/** ページ番号（1始まり）から URL を作る */
export type PageHref = (page: number) => string;

/**
 * 並びをページごとに切る。
 *
 * 0件でも空のページを1つ返す。一覧が「公開中の記事はありません」を描けるようにするため。
 */
export const paginate = <T>(items: readonly T[], perPage: number): readonly (readonly T[])[] =>
  Array.from({ length: Math.max(Math.ceil(items.length / perPage), 1) }, (_unused, index) =>
    items.slice(index * perPage, (index + 1) * perPage),
  );

/**
 * ページ番号（1始まり）から記事一覧の URL を作る。
 *
 * 1ページ目は一覧の起点（`/articles`）に置く。記事詳細（`/articles/{articleId}`）と同じ階層で
 * ページ番号を使うと、URL からどちらを指しているのか読めなくなるため、2ページ目以降は `page` の下へ置く。
 */
export const articlesPageHref = (page: number): string =>
  page === 1 ? '/articles' : `/articles/page/${String(page)}`;
