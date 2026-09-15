import type { Page } from '@playwright/test';

import { fetchAdminAlbumPage, seedDraftAlbum } from '../support/admin-api.ts';
import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, SCRATCH_CATALOG_PREFIX } from '../support/scratch-albums.ts';

const NEXT = '次のページ';
const PREVIOUS = '前のページ';
const SECOND_PAGE_API = `${stack.backendBaseUrl}/api/v1/admin/albums?page=1&size=50`;
const LAST_TITLE = 'E2E ページ送り 最後の作品';
const CATALOG_PREFIX = `${SCRATCH_CATALOG_PREFIX}PAGE-`;

/** 既存のフィクスチャを残し、検査専用の51件を登録する。既定は登録の新しい順なので末尾を先に作る。 */
const seed51Albums = async (): Promise<void> => {
  await deleteScratchAlbums();
  await seedDraftAlbum({
    title: LAST_TITLE,
    releaseDate: '2026-01-01',
    artistDisplayName: 'E2E アーティスト',
    artistSortKey: 'E2E',
    catalogNumber: `${CATALOG_PREFIX}LAST`,
  });
  for (const index of Array.from({ length: 50 }, (_, i) => i)) {
    await seedDraftAlbum({
      title: `E2E ページ送り 作品 ${String(index)}`,
      releaseDate: '2026-01-01',
      artistDisplayName: 'E2E アーティスト',
      artistSortKey: 'E2E',
      catalogNumber: `${CATALOG_PREFIX}${String(index)}`,
    });
  }
  const last = await fetchAdminAlbumPage(1, CATALOG_PREFIX);
  expect(last.totalElements).toBe(51);
  expect(last.items.map((album) => album.title)).toEqual([LAST_TITLE]);
};

const authenticate = async (page: Page): Promise<void> => {
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
};

const openList = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await authenticate(page);
  await expect(page.getByText('51 件', { exact: true })).toBeVisible();
  await expect(page.getByText('1–50 件目')).toBeVisible();
};

test.beforeEach(async ({ page }) => {
  await seed51Albums();
  /*
   * 既存フィクスチャを消さず境界の件数を固定するため、実APIの品番絞り込みを要求に付ける。
   * 応答やページ情報は作らず、照会・公開・非公開・削除のすべてを実バックエンドで通す。
   */
  await page.route(`${stack.backendBaseUrl}/api/v1/admin/albums?*`, (route) =>
    route.fallback({
      url: `${route.request().url()}&catalogNumber=${encodeURIComponent(CATALOG_PREFIX)}`,
    }),
  );
});
test.afterEach(deleteScratchAlbums);

test.describe('作品一覧のページ送り', () => {
  test('51件目を公開・非公開・削除でき、最後の1件を消すと前ページへ戻る', async ({ page }) => {
    await openList(page);
    await expect(page.getByRole('button', { name: PREVIOUS })).toBeDisabled();
    await page.getByRole('button', { name: NEXT }).click();
    await expect(page.getByText('51–51 件目')).toBeVisible();
    await expect(page.getByRole('button', { name: NEXT })).toBeDisabled();
    await expect(page.getByRole('button', { name: PREVIOUS })).toBeEnabled();
    await expect(page.getByRole('link', { name: '編集する' })).toHaveAttribute(
      'href',
      /albums\/[^/]+/u,
    );
    await captureFocused(page, page.getByRole('table'), '35a-admin-albums-page-two');

    await page.getByRole('button', { name: PREVIOUS }).click();
    await expect(page.getByText('1–50 件目')).toBeVisible();
    await page.getByRole('button', { name: NEXT }).click();
    await page.getByRole('button', { name: '公開する', exact: true }).click();
    await expect(page.getByText('51–51 件目')).toBeVisible();
    await expect(page.getByText('公開', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: '非公開にする', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: '非公開にする' }).click();
    await expect(page.getByText('下書き', { exact: true })).toBeVisible();
    await expect(page.getByText('51–51 件目')).toBeVisible();

    await page.getByRole('button', { name: '削除する' }).click();
    await expect(page.getByRole('button', { name: PREVIOUS })).toBeDisabled();
    await page.getByRole('dialog').getByRole('button', { name: '削除する' }).click();
    await expect(page.getByText('50 件', { exact: true })).toBeVisible();
    await expect(page.getByText('1–50 件目')).toBeVisible();
    await expect(page.getByRole('button', { name: NEXT })).toBeDisabled();
    await captureFocused(
      page,
      page.getByRole('navigation', { name: '作品一覧のページ送り' }),
      '35b-admin-albums-after-delete',
    );
    expect((await fetchAdminAlbumPage(1, CATALOG_PREFIX)).items).toHaveLength(0);
  });

  test('2ページ目の通信が失敗しても再試行で同じページを開く', async ({ page }) => {
    await openList(page);
    await page.route(SECOND_PAGE_API, (route) => route.abort('failed'));
    await page.getByRole('button', { name: NEXT }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await page.unroute(SECOND_PAGE_API);
    await page.getByRole('button', { name: '再試行' }).click();
    await expect(page.getByText('51–51 件目')).toBeVisible();
    await expect(page.getByText(LAST_TITLE)).toBeVisible();
  });

  test('2ページ目でセッションを断られても再認証で同じページを開く', async ({ page }) => {
    await openList(page);
    /* 応答を作らず、無効な認証を実バックエンドに断らせる。 */
    await page.route(SECOND_PAGE_API, (route) =>
      route.continue({
        headers: { ...route.request().headers(), authorization: 'Bearer e2e-invalid-session' },
      }),
    );
    await page.getByRole('button', { name: NEXT }).click();
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    await page.unroute(SECOND_PAGE_API);
    await authenticate(page);
    await expect(page.getByText('51–51 件目')).toBeVisible();
    await expect(page.getByText(LAST_TITLE)).toBeVisible();
  });
});
