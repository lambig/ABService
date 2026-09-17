import type { Page } from '@playwright/test';
import {
  renameAlbumOutsideTheScreen,
  renameArticleOutsideTheScreen,
} from '../support/admin-api.ts';
import { openAllSections, openSection } from '../support/album-editor.ts';
import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbumDetail } from '../support/scratch-albums.ts';
import {
  deleteScratchArticles,
  scratchTitlePrefix,
  seedScratchArticle,
} from '../support/scratch-articles.ts';

const RESTORE = '退避した入力を復元する';
const DISCARD = '退避データを破棄する';
const keyOf = (editor: string, target: string | null): string =>
  `abservice.admin.recovery.v1:${JSON.stringify([editor, target])}`;
const stored = (page: Page, key: string): Promise<string | null> =>
  page.evaluate((storageKey) => sessionStorage.getItem(storageKey), key);
const signIn = async (page: Page): Promise<void> => {
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く', exact: true }).click();
};
const restore = (page: Page): Promise<void> => page.getByRole('button', { name: RESTORE }).click();

test.afterEach(async () => {
  await deleteScratchArticles();
  await deleteScratchAlbums();
});

test('記事の不完全な入力を復元し、実APIの検証を直して保存すると退避も消える', async ({ page }) => {
  const article = await seedScratchArticle('入力復旧');
  const key = keyOf('article', article.articleId);
  await page.goto(`${stack.adminBaseUrl}/articles/edit?articleId=${article.articleId}`);
  await signIn(page);
  await page.getByLabel('タイトル', { exact: true }).fill('');
  await page.getByLabel('本文', { exact: true }).fill(' **書きかけの本文\n');
  await expect.poll(() => stored(page, key)).toContain('書きかけの本文');
  expect(await stored(page, key)).not.toContain(stack.adminApiKey);
  expect(await stored(page, key)).not.toContain('token');
  await page.reload();
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(article.title);
  await expect(page.getByRole('button', { name: '保存する', exact: true })).toBeDisabled();
  await expect(page.locator('iframe[title="公開記事のプレビュー"]')).toHaveCount(0);
  await captureFocused(
    page,
    page.getByRole('region', { name: '入力の復旧' }),
    '39u-editor-recovery-choice',
  );
  await restore(page);
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue(' **書きかけの本文\n');
  await expect(page.frameLocator('iframe').locator('article')).toContainText('書きかけの本文');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.locator('[data-field="title"]').getByRole('alert')).toBeVisible();
  await page.reload();
  await restore(page);
  await page.getByLabel('タイトル', { exact: true }).fill(article.title);
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.getByText('保存しました。', { exact: true })).toBeVisible();
  await expect.poll(() => stored(page, key)).toBeNull();
  await page.reload();
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue(' **書きかけの本文\n');
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
  expect(await stored(page, key)).toBeNull();
});

test('新規記事の直前入力は誤遷移から復旧でき、別editorや既存記事へ混ざらない', async ({ page }) => {
  const article = await seedScratchArticle('別対象');
  const key = keyOf('article', null);
  const title = `${scratchTitlePrefix()} 復元した新規記事 ${String(Date.now())}`;
  await page.goto(`${stack.adminBaseUrl}/articles/new`);
  await signIn(page);
  await page.getByLabel('タイトル', { exact: true }).fill(title);
  await page.getByLabel('本文', { exact: true }).fill('移動の直前まで書いた本文');
  await page.goto(`${stack.adminBaseUrl}/albums/new`);
  await openSection(page, '作品');
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue('');
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
  await page.goto(`${stack.adminBaseUrl}/articles/edit?articleId=${article.articleId}`);
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(article.title);
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
  await page.goto(`${stack.adminBaseUrl}/articles/new`);
  await restore(page);
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue('移動の直前まで書いた本文');
  await page.getByRole('button', { name: '作成する', exact: true }).click();
  await expect(page.getByText('保存しました。', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/articles\/edit\?articleId=/);
  expect(await stored(page, key)).toBeNull();
  await page.goto(`${stack.adminBaseUrl}/articles/new`);
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue('');
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
});

test('記事作成後の詳細読込が失敗しても新規作成の退避から重複作成させない', async ({ page }) => {
  const key = keyOf('article', null);
  const title = `${scratchTitlePrefix()} 作成後の読込失敗 ${String(Date.now())}`;
  await page.goto(`${stack.adminBaseUrl}/articles/new`);
  await signIn(page);
  await page.getByLabel('タイトル', { exact: true }).fill(title);
  await expect.poll(() => stored(page, key)).toContain(title);
  await page.route(`${stack.backendBaseUrl}/api/v1/admin/articles/*`, (route) =>
    route.continue({
      headers: { ...route.request().headers(), authorization: 'Bearer e2e-invalid-token' },
    }),
  );
  await page.getByRole('button', { name: '作成する', exact: true }).click();
  await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
  await expect(page).toHaveURL(/articles\/edit\?articleId=/);
  expect(await stored(page, key)).toBeNull();
  await page.unroute(`${stack.backendBaseUrl}/api/v1/admin/articles/*`);
  await signIn(page);
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(title);
  await expect(page.getByRole('button', { name: '保存する', exact: true })).toBeEnabled();
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
  await page.goto(`${stack.adminBaseUrl}/articles/new`);
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue('');
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
});

test('作品のイベントとチューンの不完全な入力も復元・通常保存できる', async ({ page }) => {
  const album = await seedScratchAlbumDetail('入力復旧');
  const key = keyOf('album', album.albumId);
  const url = `${stack.adminBaseUrl}/albums/edit?albumId=${album.albumId}`;
  await page.goto(url);
  await signIn(page);
  await openAllSections(page);
  await page.getByLabel('イベント名', { exact: true }).fill('入力途中のイベント');
  await page.getByRole('button', { name: 'トラックを追加する' }).click();
  await page.getByRole('button', { name: 'チューンを足す' }).click();
  await page.getByLabel('1チューン目の曲名').fill('復元する曲');
  await page.getByLabel('1チューン目のリンクURL').fill('not-a-url');
  await expect.poll(() => stored(page, key)).toContain('not-a-url');
  await page.reload();
  await restore(page);
  await openAllSections(page);
  await expect(page.getByLabel('イベント名', { exact: true })).toHaveValue('入力途中のイベント');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(
    page.locator('[data-field="tracks[0].tunes[0].linkUrl"]').getByRole('alert'),
  ).toBeVisible();
  await expect(page.getByLabel('1チューン目のリンクURL')).toHaveValue('not-a-url');
  await page.getByLabel('1チューン目のリンクURL').fill('');
  await page.getByLabel('開催日', { exact: true }).fill('2026-09-01');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page).toHaveURL(`${stack.adminBaseUrl}/`);
  expect(await stored(page, key)).toBeNull();
  await page.goto(url);
  await openAllSections(page);
  await expect(page.getByLabel('イベント名', { exact: true })).toHaveValue('入力途中のイベント');
  await expect(page.locator('[data-tracks]')).toContainText('復元する曲');
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
});

test('記事の古い世代を復元しても最新の保存を上書きできない', async ({ page }) => {
  const article = await seedScratchArticle('復元競合');
  await page.goto(`${stack.adminBaseUrl}/articles/edit?articleId=${article.articleId}`);
  await signIn(page);
  await page.getByLabel('本文', { exact: true }).fill('古い世代の入力');
  await expect.poll(() => stored(page, keyOf('article', article.articleId))).toContain('古い世代');
  const currentTitle = `${article.title} 別操作で保存`;
  await renameArticleOutsideTheScreen(article.articleId, currentTitle);
  await page.reload();
  await restore(page);
  await expect(
    page.getByRole('heading', { name: '編集を始めた後に、別の操作がこの記事を保存しています' }),
  ).toBeVisible();
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue('古い世代の入力');
  await captureFocused(page, page.getByRole('alert'), '39v-editor-recovery-conflict');
  const [response] = await Promise.all([
    page.waitForResponse((result) => result.request().method() === 'PUT'),
    page.getByRole('button', { name: '保存する', exact: true }).click(),
  ]);
  expect(response.status()).toBe(409);
  await page.getByRole('button', { name: '最新を読み込む' }).click();
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(currentTitle);
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue(article.body);
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
});

test('作品の古い世代も復元後に競合となり、最新の入力へ読み直せる', async ({ page }) => {
  const album = await seedScratchAlbumDetail('復元競合');
  await page.goto(`${stack.adminBaseUrl}/albums/edit?albumId=${album.albumId}`);
  await signIn(page);
  await openSection(page, '作品');
  await page.getByLabel('タイトル', { exact: true }).fill(`${album.title} 未保存`);
  await expect.poll(() => stored(page, keyOf('album', album.albumId))).toContain('未保存');
  const currentTitle = `${album.title} 別操作で保存`;
  await renameAlbumOutsideTheScreen(album.albumId, currentTitle);
  await page.reload();
  await restore(page);
  await expect(
    page.getByRole('heading', { name: '編集を始めた後に、別の操作がこの作品を保存しています' }),
  ).toBeVisible();
  const [response] = await Promise.all([
    page.waitForResponse((result) => result.request().method() === 'PUT'),
    page.getByRole('button', { name: '保存する', exact: true }).click(),
  ]);
  expect(response.status()).toBe(409);
  await page.getByRole('button', { name: '最新を読み込む' }).click();
  await openSection(page, '作品');
  await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(currentTitle);
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
});

test('復元せず破棄でき、通信失敗の入力は残り、ログアウトで全対象を消す', async ({ page }) => {
  const article = await seedScratchArticle('破棄と失敗');
  const key = keyOf('article', article.articleId);
  await page.goto(`${stack.adminBaseUrl}/articles/edit?articleId=${article.articleId}`);
  await signIn(page);
  await page.getByLabel('本文', { exact: true }).fill('破棄する入力');
  await page.reload();
  await page.getByRole('button', { name: DISCARD }).click();
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue(article.body);
  expect(await stored(page, key)).toBeNull();
  await page.getByLabel('本文', { exact: true }).fill('通信失敗でも残る入力');
  await page.route(`${stack.backendBaseUrl}/api/v1/articles/*`, (route) =>
    route.abort('connectionfailed'),
  );
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.reload();
  await restore(page);
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue('通信失敗でも残る入力');
  await page.getByRole('button', { name: 'ログアウト' }).click();
  await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
  expect(await stored(page, key)).toBeNull();
  await signIn(page);
  await expect(page.getByLabel('本文', { exact: true })).toHaveValue(article.body);
  await expect(page.getByRole('button', { name: RESTORE })).toHaveCount(0);
});
