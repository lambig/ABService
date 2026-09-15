import type { Page } from '@playwright/test';
import { EventEmitter, once } from 'node:events';
import { findArticleByTitle } from '../support/admin-api.ts';
import { revokeBrowserSession } from '../support/admin-sessions.ts';
import { albumArticle, showcase } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchArticles, seedScratchArticle } from '../support/scratch-articles.ts';

const preview = (page: Page) => page.frameLocator('iframe[title="公開記事のプレビュー"]');
const openEditor = async (page: Page, articleId?: string): Promise<void> => {
  await page.goto(
    `${stack.adminBaseUrl}/articles/${articleId === undefined ? 'new' : `edit?articleId=${articleId}`}`,
  );
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く', exact: true }).click();
  await expect(page.getByLabel('タイトル', { exact: true })).toBeVisible();
};
const openPreview = async (page: Page): Promise<void> => {
  await page.getByRole('button', { name: '公開レイアウトでプレビュー', exact: true }).click();
  await expect(preview(page).locator('article')).toBeVisible();
};
const existingArticle = async (): Promise<string> => {
  const article = await findArticleByTitle(albumArticle.title);
  return article?.articleId ?? Promise.reject(new Error('作品紹介記事がありません'));
};

test.describe('公開レイアウトのプレビュー', () => {
  test.afterEach(async () => {
    await deleteScratchArticles();
  });

  test('保存前の新規入力を描画し、閉じても入力を保つ。プレビューは書き込みを送らない', async ({
    page,
  }) => {
    await openEditor(page);
    const title = '未保存のプレビュー <title>';
    const body = '平文の本文。\n<script>window.previewExecuted=true</script>';
    await page.getByLabel('タイトル', { exact: true }).fill(title);
    await page.getByLabel('本文の形式', { exact: true }).selectOption('PLAIN_TEXT');
    await page.getByLabel('本文', { exact: true }).fill(body);
    const writes: string[] = [];
    page.on('request', (request) => {
      void (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method())
        ? writes.push(request.url())
        : undefined);
    });
    await openPreview(page);
    await expect(preview(page).getByRole('heading', { name: title, exact: true })).toBeVisible();
    await expect(preview(page).locator('article')).toContainText(body);
    await expect(preview(page).locator('article time, script')).toHaveCount(0);
    await expect(page.getByText('未公開のため公開日は表示しません。')).toBeVisible();
    const iframe = page.locator('iframe[title="公開記事のプレビュー"]');
    await expect(iframe).toHaveAttribute('sandbox', 'allow-same-origin');
    await expect(iframe).toHaveAttribute('src', '/admin/preview/article/');
    await preview(page).getByRole('link', { name: 'トップ', exact: true }).click();
    await expect(preview(page).locator('article')).toContainText(title);
    await page.getByRole('button', { name: '編集へ戻る' }).click();
    await expect(iframe).toHaveCount(0);
    await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(title);
    await expect(page.getByLabel('本文', { exact: true })).toHaveValue(body);
    await expect(
      page.getByRole('button', { name: '公開レイアウトでプレビュー', exact: true }),
    ).toBeFocused();
    expect(writes).toEqual([]);
    await openPreview(page);
    await preview(page).getByRole('link', { name: 'トップ', exact: true }).focus();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });

  test('下書きの記事を公開APIへ出さず、未保存のMarkdownを確認する', async ({ page, request }) => {
    const article = await seedScratchArticle('公開レイアウト');
    await openEditor(page, article.articleId);
    await page.getByLabel('本文の形式', { exact: true }).selectOption('MARKDOWN');
    await page.getByLabel('本文', { exact: true }).fill('## 保存前の見出し\n\n**太字**');
    await openPreview(page);
    await expect(preview(page).getByRole('heading', { name: '保存前の見出し' })).toBeVisible();
    await expect(preview(page).locator('strong')).toHaveText('太字');
    expect(
      (await request.get(`${stack.backendBaseUrl}/api/v1/articles/${article.articleId}`)).status(),
    ).toBe(404);
    expect((await request.get(`${stack.siteBaseUrl}/articles/${article.articleId}`)).status()).toBe(
      404,
    );
    await page.getByRole('button', { name: '編集へ戻る' }).click();
    await page.reload();
    await expect(page.getByLabel('本文', { exact: true })).toHaveValue(article.body);
  });

  test('公開ページと記事DOM・書体・配色が一致し、カバー・初出・額・タグを広い幅と狭い幅で確認する', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 1100 });
    const articleId = await existingArticle();
    await page.goto(`/articles/${articleId}`);
    const publicArticle = await page.locator('article').evaluate((element) => element.outerHTML);
    const publicStyle = await page.locator('article h1').evaluate((element) => {
      const style = getComputedStyle(element);
      return { font: style.fontFamily, size: style.fontSize, color: style.color };
    });
    await openEditor(page, articleId);
    await openPreview(page);
    expect(
      await preview(page)
        .locator('article')
        .evaluate((element) => element.outerHTML),
    ).toBe(publicArticle);
    expect(
      await preview(page)
        .locator('article h1')
        .evaluate((element) => {
          const style = getComputedStyle(element);
          return { font: style.fontFamily, size: style.fontSize, color: style.color };
        }),
    ).toEqual(publicStyle);
    const card = preview(page).getByRole('link').filter({ hasText: showcase.title });
    await expect(card).toContainText(showcase.eventName);
    await expect(card).toContainText(showcase.basePriceText);
    await expect(card.locator('img')).toBeVisible();
    expect(
      await card.locator('img').evaluate((image: HTMLImageElement) => image.naturalWidth),
    ).toBeGreaterThan(0);
    await expect(preview(page).locator('article header')).toContainText(albumArticle.tags[0]);
    await capture(page, '39w-article-public-layout-wide');
    await page.getByRole('button', { name: '狭い幅で確認' }).click();
    expect(
      await preview(page)
        .locator('nav ul')
        .evaluate((element) => getComputedStyle(element).flexDirection),
    ).toBe('row');
    expect(
      await preview(page)
        .locator('body')
        .evaluate((element) => element.scrollWidth <= element.clientWidth),
    ).toBe(true);
    await capture(page, '39x-article-public-layout-narrow');
    await page.getByRole('button', { name: '編集へ戻る' }).click();
    await page.getByLabel('種別', { exact: true }).selectOption('NOTE');
    await openPreview(page);
    await expect(preview(page).getByRole('heading', { name: 'この記事の作品' })).toHaveCount(0);
  });

  test('読込失敗から再試行でき、閉じた後に遅延応答で再表示しない', async ({ page }) => {
    await openEditor(page);
    const api = `${stack.backendBaseUrl}/api/v1/site-contents`;
    await page.route(api, (route) => route.fulfill({ status: 503, body: '{}' }));
    await page.getByRole('button', { name: '公開レイアウトでプレビュー', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText(
      'プレビューに必要な情報を読み込めませんでした',
    );
    await expect(page.locator('iframe')).toHaveCount(0);
    await page.unroute(api);
    await page.getByRole('button', { name: '再試行', exact: true }).click();
    await expect(preview(page).locator('article')).toBeVisible();
    await page.getByRole('button', { name: '編集へ戻る' }).click();
    const response = await page.request.get(api);
    const responseBody = await response.text();
    const pending = new EventEmitter();
    await page.route(api, async (route) => {
      await once(pending, 'release');
      await route.fulfill({ status: 200, contentType: 'application/json', body: responseBody });
    });
    const requested = page.waitForRequest(api);
    await page.getByRole('button', { name: '公開レイアウトでプレビュー', exact: true }).click();
    await requested;
    await page.getByRole('button', { name: '編集へ戻る' }).click();
    const delivered = page.waitForResponse(api);
    pending.emit('release');
    await delivered;
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator('iframe')).toHaveCount(0);
  });

  test('表示先を配信できないときも空白のプレビューで止まらない', async ({ page }) => {
    await openEditor(page);
    await page.route(`${stack.adminBaseUrl}/preview/article/`, (route) =>
      route.fulfill({
        status: 404,
        contentType: 'text/html',
        body: '<html><body>Not found</body></html>',
      }),
    );
    await page.getByRole('button', { name: '公開レイアウトでプレビュー', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('プレビューの画面を読み込めませんでした');
    await expect(page.locator('iframe')).toHaveCount(0);
  });

  test('認証が失効したときはプレビューを閉じ、入力を保持して鍵待ちへ戻る', async ({ page }) => {
    await openEditor(page, await existingArticle());
    const title = '失効前の未保存タイトル';
    await page.getByLabel('タイトル', { exact: true }).fill(title);
    await revokeBrowserSession(page);
    await page.getByRole('button', { name: '公開レイアウトでプレビュー', exact: true }).click();
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    await expect(page.locator('iframe')).toHaveCount(0);
    await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
    await page.getByRole('button', { name: '開く', exact: true }).click();
    await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(title);
  });
});
