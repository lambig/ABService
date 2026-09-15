import type { Page } from '@playwright/test';

import { stack } from '../support/config.ts';
import { capture } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { revokeBrowserSession } from '../support/admin-sessions.ts';
import { deleteScratchArticles, SCRATCH_TITLE_PREFIX } from '../support/scratch-articles.ts';

const STORAGE = 'abservice.admin.session';
const SESSIONS = `${stack.backendBaseUrl}/api/v1/admin/sessions`;
const ALBUMS = `${stack.backendBaseUrl}/api/v1/admin/albums**`;

/** 応答内容は実backendのまま、配送の時点だけを制御する。 */
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
    release: () => {
      events.dispatchEvent(new Event('release'));
    },
  };
};

const enter = async (page: Page): Promise<void> => {
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く', exact: true }).click();
};
const login = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await enter(page);
  await expect(page.getByRole('table')).toBeVisible();
};
const stored = (page: Page): Promise<{ token: string; expiresAt: string } | null> =>
  page.evaluate(
    (key) =>
      JSON.parse(sessionStorage.getItem(key) ?? 'null') as {
        token: string;
        expiresAt: string;
      } | null,
    STORAGE,
  );
const tokenOf = async (page: Page): Promise<string> => (await stored(page))?.token ?? '';
const headers = (token: string) => ({ Authorization: `Bearer ${token}` });
const flushScreen = (page: Page) =>
  page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            resolve();
          });
        });
      }),
  );

test.describe('期限付き管理セッション', () => {
  test.afterEach(deleteScratchArticles);

  test('ログアウト後の記事作成応答でURLや認証画面を変更しない', async ({ page }) => {
    const responseReady = gate();
    const delivery = gate();
    const createUrl = `${stack.backendBaseUrl}/api/v1/articles`;
    await page.route(createUrl, async (route) => {
      const response = await route.fetch();
      responseReady.release();
      await delivery.promise;
      await route.fulfill({ response });
    });
    await page.goto(`${stack.adminBaseUrl}/articles/new`);
    await enter(page);
    await page.getByLabel('タイトル').fill(`${SCRATCH_TITLE_PREFIX} 遅い作成`);
    await page.getByRole('button', { name: '作成する' }).click();
    await responseReady.promise;
    await page.getByRole('button', { name: 'ログアウト' }).click();
    const delivered = page.waitForResponse(createUrl);
    delivery.release();
    await delivered;
    await flushScreen(page);
    expect(new URL(page.url()).pathname).toMatch(/\/articles\/new\/?$/u);
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    expect(await stored(page)).toBeNull();
  });

  test('記事作成後の詳細取得が401でも、再認証後に同じ記事を開く', async ({ page }) => {
    const detailUrl = `${stack.backendBaseUrl}/api/v1/admin/articles/*`;
    await page.goto(`${stack.adminBaseUrl}/articles/new`);
    await enter(page);
    const title = `${SCRATCH_TITLE_PREFIX} 作成直後の失効`;
    await page.getByLabel('タイトル').fill(title);
    await page.route(detailUrl, async (route) => {
      await revokeBrowserSession(page);
      const response = await route.fetch();
      expect(response.status()).toBe(401);
      await route.fulfill({ response });
    });
    await page.getByRole('button', { name: '作成する' }).click();
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    const createdUrl = page.url();
    expect(new URL(createdUrl).searchParams.get('articleId')).toBeTruthy();
    expect(await stored(page)).toBeNull();
    await page.unroute(detailUrl);
    await enter(page);
    await expect(page.getByLabel('タイトル')).toHaveValue(title);
    await expect(page.getByRole('button', { name: '保存する' })).toBeVisible();
    expect(page.url()).toBe(createdUrl);
  });
  test('キーを保存せず、ページ移動とリロードで同じトークンを使う', async ({ page }) => {
    await login(page);
    const session = await stored(page);
    expect(session).toEqual({
      token: expect.stringMatching(/^abs_session_/u),
      expiresAt: expect.any(String),
    });
    expect(await page.evaluate(() => sessionStorage.getItem('abservice.admin.api-key'))).toBeNull();
    expect(await page.evaluate(() => Object.keys(sessionStorage))).toEqual([STORAGE]);
    await capture(page, '70-admin-session-authenticated');
    await page.getByRole('link', { name: '記事', exact: true }).click();
    await expect(page.getByRole('table')).toBeVisible();
    await page.reload();
    await expect(page.getByRole('table')).toBeVisible();
    expect(await stored(page)).toEqual(session);
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    await expect(page.getByRole('status')).toHaveText('ログアウトしました。');
    const rejected = await page.request.get(`${stack.backendBaseUrl}/api/v1/admin/albums`, {
      headers: headers(session?.token ?? ''),
    });
    expect(rejected.status()).toBe(401);
    await capture(page, '71-admin-session-logged-out');
  });

  test('独立タブは再認証し、保存値が複写されたタブは同じセッションの失効を受ける', async ({
    page,
    context,
  }) => {
    await login(page);
    const token = await tokenOf(page);
    const independent = await context.newPage();
    await independent.goto(stack.adminBaseUrl);
    await expect(independent.getByLabel('管理APIの鍵')).toBeVisible();
    expect(await stored(independent)).toBeNull();
    const popupReady = page.waitForEvent('popup');
    await page.evaluate(() => {
      window.open(location.href, '_blank');
    });
    const duplicate = await popupReady;
    await expect(duplicate.getByRole('table')).toBeVisible();
    expect(await tokenOf(duplicate)).toBe(token);
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await expect(page.getByRole('status')).toHaveText('ログアウトしました。');
    await duplicate.reload();
    await expect(duplicate.getByLabel('管理APIの鍵')).toBeVisible();
    expect(await stored(duplicate)).toBeNull();
    await independent.close();
    await duplicate.close();
  });

  test('失効通信に失敗してもローカルを破棄し、サーバー失効未確認と伝える', async ({ page }) => {
    await login(page);
    const token = await tokenOf(page);
    await page.route(`${SESSIONS}/current`, (route) => route.abort());
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await expect(page.getByRole('status')).toContainText('サーバーでの失効は確認できませんでした');
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    expect(await stored(page)).toBeNull();
    const stillValid = await page.request.get(`${stack.backendBaseUrl}/api/v1/admin/albums`, {
      headers: headers(token),
    });
    expect(stillValid.status()).toBe(200);
    await capture(page, '72-admin-session-revocation-unconfirmed');
    await page.request.delete(`${SESSIONS}/current`, { headers: headers(token) });
  });

  test('ログアウト後の遅い一覧応答で認証画面が復活しない', async ({ page }) => {
    const responseReady = gate();
    const delivery = gate();
    const delivered = gate();
    await page.route(ALBUMS, async (route) => {
      const response = await route.fetch();
      responseReady.release();
      await delivery.promise;
      await route.fulfill({ response });
      delivered.release();
    });
    await page.goto(stack.adminBaseUrl);
    await enter(page);
    await responseReady.promise;
    const aborted = page.waitForEvent('requestfailed', (request) =>
      request.url().includes('/admin/albums'),
    );
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await aborted;
    delivery.release();
    await delivered.promise;
    await flushScreen(page);
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    await expect(page.getByRole('table')).toHaveCount(0);
    expect(await stored(page)).toBeNull();
  });

  test('再認証後に古い401が届いても新しいセッションを壊さない', async ({ page }) => {
    const responseReady = gate();
    const delivery = gate();
    const delivered = gate();
    await page.route(ALBUMS, async (route) => {
      const response = await route.fetch({
        headers: { ...route.request().headers(), ...headers('invalid-session') },
      });
      expect(response.status()).toBe(401);
      responseReady.release();
      await delivery.promise;
      await route.fulfill({ response });
      delivered.release();
    });
    await page.goto(stack.adminBaseUrl);
    await enter(page);
    await responseReady.promise;
    await page.unroute(ALBUMS);
    const aborted = page.waitForEvent('requestfailed', (request) =>
      request.url().includes('/admin/albums'),
    );
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await aborted;
    await enter(page);
    await expect(page.getByRole('table')).toBeVisible();
    const current = await stored(page);
    delivery.release();
    await delivered.promise;
    await flushScreen(page);
    await expect(page.getByRole('table')).toBeVisible();
    expect(await stored(page)).toEqual(current);
  });

  test('取り消した認証の遅い成功は保存せず、返されたトークンを失効させる', async ({ page }) => {
    const responseReady = gate();
    const delivery = gate();
    await page.route(SESSIONS, async (route) => {
      const response = await route.fetch();
      responseReady.release();
      await delivery.promise;
      await route.fulfill({ response });
    });
    await page.goto(stack.adminBaseUrl);
    await enter(page);
    await responseReady.promise;
    await expect(page.getByLabel('管理APIの鍵')).toHaveValue('');
    await page.getByRole('button', { name: '認証を取り消す' }).click();
    const revoked = page.waitForResponse(`${SESSIONS}/current`);
    delivery.release();
    expect((await revoked).status()).toBe(204);
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    expect(await stored(page)).toBeNull();
  });

  test('期限切れの保存値と旧APIキーはリロード時に消す', async ({ page }) => {
    await login(page);
    const token = await tokenOf(page);
    await page.evaluate((key) => {
      const value = JSON.parse(sessionStorage.getItem(key) ?? '{}') as { token: string };
      sessionStorage.setItem(
        key,
        JSON.stringify({ token: value.token, expiresAt: '2000-01-01T00:00:00Z' }),
      );
      sessionStorage.setItem('abservice.admin.api-key', 'legacy-key');
    }, STORAGE);
    await page.reload();
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.length)).toBe(0);
    await page.request.delete(`${SESSIONS}/current`, { headers: headers(token) });
  });
});
