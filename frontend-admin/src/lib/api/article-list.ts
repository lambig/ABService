import type { AdminArticlePage } from './client';

/**
 * 記事一覧のページ送り。
 *
 * <p>
 * 応答が返したページ情報だけから求める。総件数や総ページ数を画面が数え直さない——数え直すと、削除や
 * 別の操作で母集団が変わったときに、画面の数と応答の数が食い違う。
 * </p>
 */

/** 1件も読めていない状態のページ。読み込み前の表示に使う */
export const EMPTY_PAGE: AdminArticlePage = {
  items: [],
  page: 0,
  size: 0,
  totalElements: 0,
  totalPages: 0,
};

/**
 * 実際に読めるページ番号。
 *
 * <p>
 * 最終ページの最後の1件を消すと、いま見ているページ番号は範囲の外に出る。応答が返した総ページ数から
 * 求め直すことで、空の表を出さずに1つ手前へ戻れる。1件も無いときは先頭（0）。
 * </p>
 *
 * @param page 応答が返したページ
 * @returns 範囲に収まるページ番号
 */
export const availablePageOf = (page: AdminArticlePage): number =>
  Math.min(page.page, Math.max(page.totalPages - 1, 0));

/**
 * いま出している範囲（1 始まりの通し番号）。
 *
 * <p>
 * 0 件のときは `first` が `last` を超える。範囲そのものを出すかどうかは呼び出し側が決める（件数の
 * 表示を持たない画面もあるため、ここでは空を表す別の形を作らない）。
 * </p>
 *
 * @param page 応答が返したページ
 * @returns 先頭と末尾の通し番号
 */
export const rangeOf = (page: AdminArticlePage): Readonly<{ first: number; last: number }> => ({
  first: page.page * page.size + 1,
  last: Math.min((page.page + 1) * page.size, page.totalElements),
});

/**
 * 先頭のページか（前へ送れない）。
 *
 * <p>
 * 「送れる」ではなく「端にいる」を返す。画面が要るのは送りの導線を塞ぐ条件で、肯定形のまま扱える
 * （CODING_GUIDELINES §9）。
 * </p>
 *
 * @param page 応答が返したページ
 */
export const isFirstPage = (page: AdminArticlePage): boolean => page.page === 0;

/**
 * 最後のページか（次へ送れない）。1件も無いときも端として扱う。
 *
 * @param page 応答が返したページ
 */
export const isLastPage = (page: AdminArticlePage): boolean => page.page + 1 >= page.totalPages;
