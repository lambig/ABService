import type { Locator, Page } from '@playwright/test';

import { findAlbumByCatalogNumber, findArticleByTitle } from '../support/admin-api.ts';
import { albumArticle, showcase } from '../support/build-fixtures.ts';
import { capture } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 公開サイトの狭い幅での見えかた（#341）。
 *
 * 他のシナリオは広い幅で走る。幅は project の `use` が持つため、ここだけファイル単位で上書きする
 * （ファイルの指定は project の指定より強い）。撮る証跡も広い幅のものと別の名前にし、同じ画面の
 * 広狭を並べて読めるようにする。
 *
 * 幅で変わるのは2箇所だけ（`albums/index.astro` の一覧と `albums/[albumId].astro` のヘッダー）で、
 * 残りは `BaseLayout.astro` の `max-w-5xl px-4` による流動。したがってここで見るのは、切り替わりが
 * 効いていることと、**どのページにも横スクロールが出ていないこと**の2つ。
 */

/** 手のひらの端末に相当する幅。Tailwind の `sm`（640px）の下側を代表させる */
const NARROW_VIEWPORT = { width: 390, height: 844 };

/** シードされた公開中の作品の件数。1列に積まれることを件数とともに見る */
const PUBLISHED_ALBUM_COUNT = 2;

test.use({ viewport: NARROW_VIEWPORT });

/**
 * 横にはみ出した量。狭い幅で最初に壊れるのは、幅を固定した要素が器から出るときで、それはこの差に出る。
 */
const horizontalOverflowOf = async (page: Page): Promise<number> =>
  page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);

const boxOf = async (
  locator: Locator,
): Promise<{ x: number; y: number; width: number; height: number }> =>
  (await locator.boundingBox()) ??
  Promise.reject(new Error('要素が画面に無いため、位置を取れません'));

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

test.describe('狭い幅の公開サイト', () => {
  test('トップは横へはみ出さない', async ({ page }) => {
    await page.goto('/');

    expect(await horizontalOverflowOf(page)).toBe(0);

    await capture(page, '70-narrow-top');
  });

  test('作品の一覧は1列に積まれる', async ({ page }) => {
    await page.goto('/albums');

    /* 主要な導線もリストのため、本文の側へ絞る */
    const cards = page.getByRole('main').getByRole('listitem');
    await expect(cards).toHaveCount(PUBLISHED_ALBUM_COUNT);

    /*
     * 広い幅では横に2枚並ぶ（`sm:grid-cols-2`）。狭い幅では左端が揃い、上下に積まれる。段組みの
     * 数を数える代わりに、2枚の位置関係で見る。
     */
    const first = await boxOf(cards.nth(0));
    const second = await boxOf(cards.nth(1));

    expect(second.x).toBe(first.x);
    expect(second.y).toBeGreaterThan(first.y + first.height - 1);

    expect(await horizontalOverflowOf(page)).toBe(0);

    await capture(page, '71-narrow-albums-list');
  });

  test('作品の詳細は、埋め込み枠を含めて横へはみ出さない', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    /*
     * 埋め込みは幅を持つ要素のうち唯一の外部由来で、器の幅に従わせている（`w-full`）。遮断されて
     * いても枠は場所を取るため、はみ出しの検査はこの作品で行う。
     */
    const embed = await boxOf(page.locator('iframe'));
    expect(embed.width).toBeLessThanOrEqual(NARROW_VIEWPORT.width);

    expect(await horizontalOverflowOf(page)).toBe(0);

    await capture(page, '72-narrow-album-detail');
  });

  test('記事の詳細は、本文の画像を含めて横へはみ出さない', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    expect(await horizontalOverflowOf(page)).toBe(0);

    await capture(page, '73-narrow-article-detail');
  });
});
