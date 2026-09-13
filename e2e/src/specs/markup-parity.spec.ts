import type { Locator, Page } from '@playwright/test';

import { findArticleByTitle } from '../support/admin-api.ts';
import { albumArticle } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * プレビューと公開の一致（#289 から #122 へ引き継いだ受け入れ条件）。
 *
 * 描画が同じ関数であることは共有パッケージが保証するが、**同じ設定値で呼んでいるか**は画面を結合して
 * みないと分からない。配信ベースパスが片方だけ違えば、公開では出る画像がプレビューでは落ちる。同じ
 * 記事の同じ本文を、公開ページと管理画面のプレビューの双方で見て、残る画像が一致することを確かめる。
 */

/** 鍵の入力欄のラベルと、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 本文を描いている区画。公開サイトと管理画面で当てているクラス・印は別 */
const publicBodyOf = (page: Page): Locator => page.locator('.prose-body');
const previewOf = (page: Page): Locator => page.locator('[data-preview="markdown"]');

const articleOf = async (title: string): Promise<{ articleId: string }> => {
  const article = await findArticleByTitle(title);

  return article ?? Promise.reject(new Error(`シードした記事が見つかりません: ${title}`));
};

/**
 * 配信ベース配下の画像だけが残っていること。
 *
 * 逸脱する `src` は画像ごと落ちる（書き換えて救済しない）ため、本文に描かれる画像は1つになる。
 */
const expectOnlyAllowedImage = async (body: Locator): Promise<void> => {
  await expect(body.locator(`img[src="${albumArticle.image.allowedSrc}"]`)).toHaveCount(1);
  await expect(body.locator('img')).toHaveCount(1);
};

test.describe('プレビューと公開の一致', () => {
  test('画像の配信ベース判定が、公開ページとプレビューで同じになる', async ({ page }) => {
    const { articleId } = await articleOf(albumArticle.title);

    await page.goto(`/articles/${articleId}`);
    await expect(
      publicBodyOf(page).getByRole('heading', { name: albumArticle.body.heading }),
    ).toBeVisible();
    await expectOnlyAllowedImage(publicBodyOf(page));

    /*
     * 証跡は本文の区画を撮る。**配信ベース配下の画像1つだけが枠として残り、逸脱する画像は枠ごと
     * 出ない**ことが読み取れる（実体を置いていないため、残った1つは壊れ画像として写る。実アセットで
     * 見た目まで確かめるのは #164 のカバー画像のジャーニー）。
     */
    await captureFocused(page, publicBodyOf(page), '58-article-body-images-public');

    /* 同じ記事を管理画面で開く。プレビューが描くのは、公開ページと同じ本文である */
    await page.goto(`${stack.adminBaseUrl}/articles/edit?articleId=${articleId}`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await expect(
      previewOf(page).getByRole('heading', { name: albumArticle.body.heading }),
    ).toBeVisible();
    await expectOnlyAllowedImage(previewOf(page));

    /* 公開ページと並べて読めるよう、同じ本文のプレビューも撮る */
    await captureFocused(page, previewOf(page), '59-article-body-images-preview');
  });
});
