import { albumArticle, pagination, siteContent } from '../support/build-fixtures.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * トップ（#123）のジャーニー。
 *
 * サイト名と紹介文はリポジトリに持たず、管理画面から登録した文言を引く（#230）。ここで確かめるのは
 * 「登録した文言が出ること」で、文言そのものはフィクスチャが持つ。
 */

/** トップに並べる記事の件数。画面の実装が持つ（#197） */
const HOME_ARTICLE_COUNT = 5;

test.describe('トップ', () => {
  test('サイト名と紹介文が出る', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle(siteContent.name);
    await expect(page.getByRole('link', { name: siteContent.name })).toBeVisible();

    await expect(
      page.getByRole('heading', { level: 2, name: siteContent.introduction.heading }),
    ).toBeVisible();
    await expect(page.getByText(siteContent.introduction.lead)).toBeVisible();
  });

  test('記事が公開日の降順で5件だけ並ぶ', async ({ page }) => {
    await page.goto('/');

    /* 記事のカードは main の中の項目。導線（ヘッダー）の項目と混ざらないよう main へ絞る */
    const cards = page.locator('main li');
    await expect(cards).toHaveCount(HOME_ARTICLE_COUNT);

    const titles = await cards.getByRole('heading', { level: 2 }).allInnerTexts();
    expect(titles[0]).toBe(albumArticle.title);

    /*
     * 全体を辿るのは記事一覧の役割。ここには続きが出ない。完全一致で見るのは、詰め物の1番が
     * 10番台のタイトルの一部にもなるため（「記事 1」は「記事 19」に含まれる）。
     */
    await expect(page.getByText(pagination.titleOf(1), { exact: true })).toHaveCount(0);
  });
});
