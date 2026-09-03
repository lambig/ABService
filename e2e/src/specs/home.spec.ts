import { albumArticle, pagination, plainArticle, siteContent } from '../support/build-fixtures.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * トップ（#123）のジャーニー。
 *
 * サイト名と紹介文はリポジトリに持たず、管理画面から登録した文言を引く（#230）。ここで確かめるのは
 * 「登録した文言が出ること」で、文言そのものはフィクスチャが持つ。
 */

/** トップに並べる記事の件数。画面の実装が持つ（#197） */
const HOME_ARTICLE_COUNT = 5;

/**
 * トップに並ぶはずのタイトル。
 *
 * 記事の並びは公開日の降順で、最後に公開した作品紹介・ノートが先頭へ来る。続きは詰め物が番号の大きい方
 * から埋め、5件で打ち切られる。
 */
const homeArticleTitles = [
  albumArticle.title,
  plainArticle.title,
  ...Array.from({ length: HOME_ARTICLE_COUNT - 2 }, (_unused, index) =>
    pagination.titleOf(pagination.filler - index),
  ),
];

test.describe('トップ', () => {
  test('サイト名と紹介文が出る', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle(siteContent.name);

    /* ページ固有の名前を持たないため、サイト名がこのページの見出しになる */
    await expect(page.getByRole('heading', { level: 1, name: siteContent.name })).toBeVisible();

    await expect(
      page.getByRole('heading', { level: 2, name: siteContent.introduction.heading }),
    ).toBeVisible();
    await expect(page.getByText(siteContent.introduction.lead)).toBeVisible();
  });

  test('記事が公開日の降順で5件だけ並ぶ', async ({ page }) => {
    await page.goto('/');

    /*
     * 記事のカードは「記事」の区画にぶら下がるため h3。件数・順序・打ち切り位置を1つの期待値で見る。
     */
    const titles = await page.getByRole('heading', { level: 3 }).allInnerTexts();

    expect(titles).toEqual(homeArticleTitles);
  });
});
