import { stack } from '../support/config.ts';
import { captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchArticles, seedScratchArticle } from '../support/scratch-articles.ts';

test.afterEach(deleteScratchArticles);

test('記事の入力支援は通信なしで使え、backendの検証後に通常入力と一緒に保存される', async ({
  page,
  context,
}) => {
  const article = await seedScratchArticle('入力支援');
  await page.goto(`${stack.adminBaseUrl}/articles/edit?articleId=${article.articleId}`);
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
  const body = page.getByLabel('本文', { exact: true });
  await expect(body).toHaveValue(article.body);
  await expect(page.getByRole('group', { name: 'Markdown入力支援' })).toHaveCount(0);
  await page.getByLabel('本文の形式', { exact: true }).selectOption('MARKDOWN');
  await body.fill('音楽の紹介');
  await body.press('ControlOrMeta+A');
  await context.setOffline(true);
  await page.getByRole('button', { name: '太字', exact: true }).click();
  await expect(body).toHaveValue('**音楽の紹介**');
  await expect(body).toBeFocused();
  await body.press('ArrowRight');
  await body.press('End');
  await body.press('Enter');
  await body.pressSequentially('通常入力も保存します。');
  const expected = '**音楽の紹介**\n通常入力も保存します。';
  await expect(body).toHaveValue(expected);
  await expect(
    page
      .frameLocator('iframe[title="公開記事のプレビュー"]')
      .locator('[data-article-body] .prose-body strong'),
  ).toHaveText('音楽の紹介');
  await captureFocused(
    page,
    page.locator('[data-field="body"]'),
    '39s-article-markdown-assistance',
  );
  await context.setOffline(false);
  await page.getByLabel('タイトル', { exact: true }).fill('');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.locator('[data-field="title"]').getByRole('alert')).toBeVisible();
  await expect(body).toHaveValue(expected);
  await page.getByLabel('タイトル', { exact: true }).fill(article.title);
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('保存しました。');
  await page.reload();
  await expect(body).toHaveValue(expected);
  await page.getByLabel('本文の形式', { exact: true }).selectOption('PLAIN_TEXT');
  await expect(page.getByRole('group', { name: 'Markdown入力支援' })).toHaveCount(0);
  await expect(body).toHaveValue(expected);
});

test('サイト文言でも同じ入力支援で入力して保存・再読込できる', async ({ page }) => {
  await page.goto(`${stack.adminBaseUrl}/site-contents`);
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く' }).click();
  await expect(page.getByRole('table')).toBeVisible();
  /* 削除できない文言は既存E2Eと同じ固定キーを使い回す。 */
  const key = 'e2e.scratch.text';
  await page.getByLabel('キー', { exact: true }).fill(key);
  await page.getByLabel('形式', { exact: true }).selectOption('MARKDOWN');
  const body = page.getByLabel('本文', { exact: true });
  await body.fill('sample');
  await body.press('ControlOrMeta+A');
  await page.getByRole('button', { name: 'コード', exact: true }).click();
  await expect(body).toHaveValue('`sample`');
  await expect(page.locator('[data-preview] code')).toHaveText('sample');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: key })).toContainText('`sample`');
  await page.reload();
  await page
    .getByRole('row')
    .filter({ hasText: key })
    .getByRole('button', { name: '編集する' })
    .click();
  await expect(body).toHaveValue('`sample`');
  await captureFocused(
    page,
    page.locator('[data-field="content"]'),
    '39t-site-markdown-assistance',
  );
});
