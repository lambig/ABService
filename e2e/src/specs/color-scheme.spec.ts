import type { Page } from '@playwright/test';

import { findAlbumByCatalogNumber, findArticleByTitle } from '../support/admin-api.ts';
import { albumArticle, showcase } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * ダークの組（#341）。
 *
 * <p>
 * 配色は閲覧者の OS 設定に従い、サイト内での切り替え機構は持たない（DECISIONS 25）。つまりダークは
 * 将来の任意機能ではなく、**入れた時点で出る経路**になる。他のシナリオはすべてライトで走るため、
 * ここだけファイル単位で配色を上書きする。
 * </p>
 *
 * <p>
 * 見るのは主に絵（#341 が挙げる主要ページと、差し色・警告色・罫線が同じ画面に並ぶ管理画面）。あわせて
 * **組が実際に当たっていること**を地の色の明るさで固定する——絵だけだと、ダークの定義が丸ごと落ちても
 * 撮り直すまで気付けない。
 * </p>
 */

test.use({ colorScheme: 'dark' });

/** 鍵の入力欄のラベルと、鍵を送る操作。文言は画面の実装が持つ */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/**
 * 地が暗いと言える明るさの上限。
 *
 * 0 が黒、1 が白。ライトの地（`oklch(0.97 …)`）とダークの地（`oklch(0.19 …)`）は十分に離れているため、
 * 中間に閾値を置けば「どちらの組が当たっているか」を取り違えない。トークンの微調整では動かない。
 */
const DARK_BACKGROUND_MAX_LUMINANCE = 0.5;

/**
 * 地の色の明るさ（0 が黒、1 が白）。
 *
 * 色は `oklch` で書いてあり、計算済みの値もその色空間のまま返る。文字列を自分で解くと色空間ごとに
 * 場合分けが増えるため、**ブラウザに解かせて**画素として読む。重みは相対輝度の近似。
 */
const backgroundLuminanceOf = async (page: Page): Promise<number> =>
  page.evaluate(() => {
    const context =
      document.createElement('canvas').getContext('2d') ??
      (() => {
        throw new Error('画素を読めないため、地の色の明るさを取れません');
      })();

    context.fillStyle = getComputedStyle(document.body).backgroundColor;
    context.fillRect(0, 0, 1, 1);
    const [red, green, blue] = context.getImageData(0, 0, 1, 1).data;
    return (0.2126 * (red ?? 0) + 0.7152 * (green ?? 0) + 0.0722 * (blue ?? 0)) / 255;
  });

const expectDarkPalette = async (page: Page): Promise<void> => {
  expect(await backgroundLuminanceOf(page)).toBeLessThan(DARK_BACKGROUND_MAX_LUMINANCE);
};

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

test.describe('ダークの組の公開サイト', () => {
  test('トップが読める', async ({ page }) => {
    await page.goto('/');

    await expectDarkPalette(page);
    await capture(page, '90-dark-top');
  });

  test('作品の一覧が読める', async ({ page }) => {
    await page.goto('/albums');

    await expectDarkPalette(page);
    /* カードの分離は罫線が担う（角丸をほぼ落としているため）。暗い地でも境が見えることを絵で見る */
    await capture(page, '91-dark-albums-list');
  });

  test('作品の詳細が読める', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    await expectDarkPalette(page);
    /* 外部の埋め込み枠は自前の配色を持つ。器の暗さと競らないことを絵で見る */
    await capture(page, '92-dark-album-detail');
  });

  test('記事の一覧が読める', async ({ page }) => {
    await page.goto('/articles');

    await expectDarkPalette(page);
    await capture(page, '93-dark-articles-list');
  });

  test('記事の詳細が読める', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    await expectDarkPalette(page);
    await capture(page, '94-dark-article-detail');
  });
});

test.describe('ダークの組の管理画面', () => {
  test('一覧で差し色・警告色・罫線が並んで読める', async ({ page }) => {
    await page.goto(stack.adminBaseUrl);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    await expectDarkPalette(page);

    /*
     * 管理画面は `--border` / `--input` を公開サイトより一段はっきりさせている（#341）。差し色の
     * 状態バッジと `--destructive` の削除、罫線と入力の輪郭が同じ画面に並ぶのはここだけ。
     */
    await capture(page, '95-dark-admin-albums');
  });
});
