import type { Page } from '@playwright/test';

import { publishAlbum } from '../support/admin-api.ts';
import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbumDetail } from '../support/scratch-albums.ts';

const CHECK = '現在の状態を確認';
const TIMEOUT = '通信が30秒以内に完了しませんでした。';

/** 応答の内容を変えず、実通信を送る／返す時点だけを制御する。 */
const gate = () => {
  const events = new EventTarget();
  const promise = new Promise<void>((resolve) => {
    events.addEventListener(
      'release',
      () => {
        resolve();
      },
      { once: true },
    );
  });
  return {
    promise,
    resolve: () => {
      events.dispatchEvent(new Event('release'));
    },
  };
};

const openList = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

test.afterEach(deleteScratchAlbums);

test('公開中のログアウトで待機を中断し、遅い完了を新しいセッションへ反映しない', async ({
  page,
}) => {
  const album = await seedScratchAlbumDetail('公開中断');
  await openList(page);
  const url = `${stack.backendBaseUrl}/api/v1/albums/${album.albumId}/publish`;
  const committed = gate();
  const release = gate();
  const delivered = gate();
  await page.route(url, async (route) => {
    const response = await route.fetch();
    expect(response.ok()).toBe(true);
    committed.resolve();
    await release.promise;
    await route.fulfill({ response });
    delivered.resolve();
  });
  await page
    .getByRole('row')
    .filter({ hasText: album.title })
    .getByRole('button', { name: '公開する' })
    .dblclick();
  await committed.promise;
  const aborted = page.waitForEvent('requestfailed', (request) => request.url() === url);
  await page.getByRole('button', { name: 'ログアウト' }).click();
  await aborted;
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
  await expect(page.getByRole('table')).toBeVisible();
  release.resolve();
  await delivered.promise;
  await expect(page.getByRole('row').filter({ hasText: album.title })).toContainText('公開');
  await expect(page.getByRole('region', { name: '操作結果の確認' })).toHaveCount(0);
  expect(
    (await page.requests()).filter(
      (request) => request.url() === url && request.method() === 'POST',
    ),
  ).toHaveLength(1);
});

test('一覧の応答待ちを期限で抜け、同じページを再照会できる', async ({ page }) => {
  await page.clock.install();
  await openList(page);
  const held = gate();
  const release = gate();
  const url = `${stack.backendBaseUrl}/api/v1/admin/albums?*`;
  await page.route(url, async (route) => {
    held.resolve();
    await release.promise;
    await route.abort();
  });
  await page.reload();
  await held.promise;
  await page.clock.fastForward(30_001);
  await expect(page.getByRole('alert')).toHaveText(TIMEOUT);
  release.resolve();
  await page.unroute(url);
  await page.getByRole('button', { name: '再試行' }).click();
  await expect(page.getByRole('table')).toBeVisible();
});

test('影響範囲の照会が期限切れなら実行できず、照会だけをやり直せる', async ({ page }) => {
  const album = await seedScratchAlbumDetail('照会期限');
  await page.clock.install();
  await openList(page);
  const held = gate();
  const release = gate();
  const url = `${stack.backendBaseUrl}/api/v1/admin/albums/${album.albumId}/preconditions*`;
  await page.route(url, async (route) => {
    held.resolve();
    await release.promise;
    await route.abort();
  });
  await page
    .getByRole('row')
    .filter({ hasText: album.title })
    .getByRole('button', { name: '削除する' })
    .click();
  await held.promise;
  await page.clock.fastForward(30_001);
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText(TIMEOUT);
  await expect(dialog.getByRole('button', { name: '削除する' })).toBeDisabled();
  release.resolve();
  await page.unroute(url);
  await dialog.getByRole('button', { name: 'もう一度確認する' }).click();
  await expect(dialog.getByRole('button', { name: '削除する' })).toBeEnabled();
  await dialog.getByRole('button', { name: 'やめる' }).click();
});

for (const operation of ['publish', 'unpublish', 'delete'] as const) {
  test(`${operation}が実際に完了した後の応答喪失で、再送せず現在値を照会する`, async ({ page }) => {
    const album = await seedScratchAlbumDetail('結果不明');
    await (operation === 'unpublish' ? publishAlbum(album.albumId) : Promise.resolve());
    await page.clock.install();
    await openList(page);
    const url = `${stack.backendBaseUrl}/api/v1/albums/${album.albumId}${operation === 'delete' ? '' : `/${operation}`}`;
    const committed = gate();
    const release = gate();
    await page.route(url, async (route) => {
      const response = await route.fetch();
      expect(response.ok()).toBe(true);
      committed.resolve();
      // LOST-RESPONSE: 実APIの更新を完了させてから返送だけを止め、架空の成功応答を作らない。
      await (operation === 'publish' ? release.promise : Promise.resolve());
      await route.abort();
    });
    const label = { publish: '公開する', unpublish: '非公開にする', delete: '削除する' }[operation];
    await page
      .getByRole('row')
      .filter({ hasText: album.title })
      .getByRole('button', { name: label })
      .click();
    await (operation === 'publish'
      ? Promise.resolve()
      : page.getByRole('dialog').getByRole('button', { name: label }).click());
    await committed.promise;
    await (operation === 'publish' ? page.clock.fastForward(30_001) : Promise.resolve());
    const recovery = page.getByRole('region', { name: '操作結果の確認' });
    await expect(recovery).toBeVisible();
    release.resolve();
    await expect(recovery.getByRole('button', { name: '一覧を読み直す' })).toHaveCount(0);
    await recovery.getByRole('button', { name: CHECK }).click();
    const observed = {
      publish: '現在の状態：公開。',
      unpublish: '現在の状態：下書き。',
      delete: '現在、この作品は見つかりません。',
    }[operation];
    await expect(recovery).toContainText(observed);
    await captureFocused(page, recovery, `36-${operation}-result-recovery`);
    await recovery.getByRole('button', { name: '一覧を読み直す' }).click();
    await expect(page.getByRole('table')).toBeVisible();
    expect(
      (await page.requests()).filter(
        (request) => request.url() === url && ['POST', 'DELETE'].includes(request.method()),
      ),
    ).toHaveLength(1);
  });
}
