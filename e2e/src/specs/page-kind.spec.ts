import type { Page } from '@playwright/test';

import { findAlbumByCatalogNumber, findArticleByTitle } from '../support/admin-api.ts';
import { albumArticle, quiet, showcase } from '../support/build-fixtures.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 作品のページと記事のページの見分け（#353）。
 *
 * <p>
 * 内容が近く、見出しの下の1行目まで同じ形になる。読み手が「いま作品そのものを見ているのか、作品に
 * ついて書かれた記事を見ているのか」を掴む手がかりとして、見出しの上に印を置く。
 * </p>
 *
 * <p>
 * ここで見るのは**取り違えないこと**である。片方に印があることだけを見ると、両方に同じ印が出ていても
 * 通る。それぞれのページで、相手の印が出ていないことまで確かめる。
 * </p>
 */

/** 印の文言。画面の実装が持つ */
const WORK_MARK = 'Work';
const ARTICLE_MARK = 'Article';

/** 記事の詳細に出してはいけない種別の文言（#346） */
const ALBUM_TYPE_LABEL = '作品紹介';

const albumPathOf = async (catalogNumber: string): Promise<string> => {
  const album = await findAlbumByCatalogNumber(catalogNumber);

  return album === undefined
    ? Promise.reject(new Error(`シードした作品が見つかりません: ${catalogNumber}`))
    : `/albums/${album.albumId}`;
};

const articlePathOf = async (title: string): Promise<string> => {
  const article = await findArticleByTitle(title);

  return article === undefined
    ? Promise.reject(new Error(`シードした記事が見つかりません: ${title}`))
    : `/articles/${article.articleId}`;
};

/** ページの見出しの区画。印は見出しの上に置く */
const headerOf = (page: Page) => page.locator('article header');

test.describe('作品のページと記事のページの見分け', () => {
  test('作品の詳細は、見出しの上で作品と名乗る', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    await expect(headerOf(page)).toContainText(WORK_MARK);
    await expect(headerOf(page)).not.toContainText(ARTICLE_MARK);

    await captureFocused(page, headerOf(page), '05b-album-detail-kind');
  });

  test('記事の詳細は、見出しの上で記事と名乗る', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    await expect(headerOf(page)).toContainText(ARTICLE_MARK);
    await expect(headerOf(page)).not.toContainText(WORK_MARK);

    /*
     * 出すのはページの種類であって、記事の種別ではない（#346 が落としたのは後者）。分類名が戻って
     * いないことを、同じ画面で見る。
     */
    await expect(page.getByText(ALBUM_TYPE_LABEL, { exact: true })).toHaveCount(0);

    await captureFocused(page, headerOf(page), '10c-article-detail-kind');
  });

  test('一覧のカードも、詳細と同じ印を持つ', async ({ page }) => {
    await page.goto('/albums');
    await expect(page.getByRole('link').filter({ hasText: quiet.title })).toContainText(WORK_MARK);

    await page.goto('/articles');
    await expect(page.getByRole('link').filter({ hasText: albumArticle.title })).toContainText(
      ARTICLE_MARK,
    );
  });
});
