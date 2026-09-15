import type { Page } from '@playwright/test';
import { EventEmitter, once } from 'node:events';
import { findArticleByTitle } from '../support/admin-api.ts';
import { revokeBrowserSession } from '../support/admin-sessions.ts';
import { albumArticle, showcase, showcaseTracks } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture, captureWhole } from '../support/evidence.ts';
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

  test('右側に常時表示し、入力に追従する。入力中は再取得・書き込みを送らない', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1100 });
    await openEditor(page);
    await openPreview(page);
    const writes: string[] = [];
    page.on('request', (request) => {
      void (request.url().includes('/api/') ? writes.push(request.url()) : undefined);
    });
    const title = '未保存のプレビュー <title>';
    const body = '平文の本文。\n<script>window.previewExecuted=true</script>';
    await page.getByLabel('タイトル', { exact: true }).fill(title);
    await page.getByLabel('本文の形式', { exact: true }).selectOption('PLAIN_TEXT');
    await page.getByLabel('本文', { exact: true }).fill(body);
    await openPreview(page);
    await expect(preview(page).getByRole('heading', { name: title, exact: true })).toBeVisible();
    await expect(preview(page).locator('article')).toContainText(body);
    await expect(preview(page).locator('article time, script')).toHaveCount(0);
    await expect(page.getByText('未公開のため公開日は表示しません。')).toBeVisible();
    const iframe = page.locator('iframe[title="公開記事のプレビュー"]');
    await expect(iframe).toHaveAttribute('sandbox', 'allow-same-origin allow-scripts');
    await expect(iframe).toHaveAttribute('src', '/admin/preview/article/');
    await preview(page).getByRole('link', { name: 'トップ', exact: true }).click();
    await expect(preview(page).locator('article')).toContainText(title);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    const input = page.getByLabel('タイトル', { exact: true });
    const inputBox = await input.boundingBox();
    const previewBox = await iframe.boundingBox();
    expect(previewBox?.x).toBeGreaterThan((inputBox?.x ?? 0) + (inputBox?.width ?? 0));
    await input.fill('入力中の更新');
    await expect(
      preview(page).getByRole('heading', { name: '入力中の更新', exact: true }),
    ).toBeVisible();
    await expect(input).toBeFocused();
    await page.getByLabel('本文の形式', { exact: true }).selectOption('MARKDOWN');
    await page.getByLabel('本文', { exact: true }).fill('**入力中の本文**');
    await expect(preview(page).locator('strong')).toHaveText('入力中の本文');
    expect(writes).toEqual([]);
    await page
      .getByLabel('本文', { exact: true })
      .fill(Array.from({ length: 80 }, (_, index) => `段落 ${String(index)}`).join('\n\n'));
    await page.getByRole('button', { name: '作成する', exact: true }).scrollIntoViewIfNeeded();
    await expect(iframe).toBeInViewport();
    expect(
      await preview(page)
        .locator('html')
        .evaluate((element) => element.scrollHeight > element.clientHeight),
    ).toBe(true);
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
    await page.reload();
    await expect(page.getByLabel('本文', { exact: true })).toHaveValue(article.body);
  });

  test('公開ページと記事DOM・書体・配色が一致し、作品詳細を広い幅と狭い幅で確認する', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 1100 });
    const articleId = await existingArticle();
    await page.goto(`/articles/${articleId}`);
    await captureWhole(page, '39y-article-expanded-public');
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
    const card = preview(page).locator('[data-public-album]');
    await expect(card).toContainText(showcase.eventName);
    await expect(card).toContainText(showcase.basePriceText);
    await expect(card.locator('iframe')).toHaveAttribute('src', /w\.soundcloud\.com/u);
    await expect(card.locator('[data-album-tracks]')).toContainText(
      showcaseTracks.titledWithTune.name,
    );
    await expect(card.locator('header img')).toHaveCount(0);
    await expect(preview(page).locator('[data-article-header]')).toContainText(
      albumArticle.tags[0],
    );
    await page.evaluate(() => {
      window.scrollTo(0, 0);
    });
    await capture(page, '39w-article-expanded-preview-wide');
    await page.setViewportSize({ width: 390, height: 844 });
    const inputBox = await page.getByLabel('本文', { exact: true }).boundingBox();
    const previewBox = await page.locator('iframe').boundingBox();
    expect(previewBox?.y).toBeGreaterThan((inputBox?.y ?? 0) + (inputBox?.height ?? 0));
    expect(await page.evaluate(() => document.body.scrollWidth <= window.innerWidth)).toBe(true);
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
    await captureWhole(page, '39x-article-expanded-preview-mobile');
    await page.getByLabel('種別', { exact: true }).selectOption('NOTE');
    await openPreview(page);
    await expect(preview(page).locator('[data-public-album]')).toHaveCount(0);
  });

  test('試聴の操作を入力中も保ち、プレビュー文書内のスクリプトは実行しない', async ({ page }) => {
    await page.route('https://w.soundcloud.com/**', (route) =>
      route.fulfill({
        contentType: 'text/html',
        body: '<button>0</button><script>document.querySelector("button").onclick = event => event.target.textContent = String(Number(event.target.textContent) + 1)</script>',
      }),
    );
    await openEditor(page, await existingArticle());
    await openPreview(page);
    const player = preview(page).frameLocator('iframe').getByRole('button');
    await player.click();
    await expect(player).toHaveText('1');
    await page.getByLabel('タイトル', { exact: true }).fill('試聴中の編集');
    await page.getByLabel('本文', { exact: true }).fill('入力後の本文');
    await expect(preview(page).locator('[data-article-body]')).toContainText('入力後の本文');
    await expect(player).toHaveText('1');
    await preview(page)
      .locator('body')
      .evaluate((element) => {
        const script = element.ownerDocument.createElement('script');
        script.textContent = 'document.body.dataset.previewExecuted="yes"';
        element.append(script);
      });
    await expect(preview(page).locator('body')).not.toHaveAttribute('data-preview-executed');
  });

  test('読込失敗から再試行でき、ログアウト後に遅延応答で再表示しない', async ({ page }) => {
    const api = `${stack.backendBaseUrl}/api/v1/site-contents`;
    await page.route(api, (route) => route.fulfill({ status: 503, body: '{}' }));
    await openEditor(page);
    await expect(page.getByRole('alert')).toContainText(
      'プレビューに必要な情報を読み込めませんでした',
    );
    await expect(page.locator('iframe')).toHaveCount(0);
    await page.unroute(api);
    await page.getByRole('button', { name: '再試行', exact: true }).click();
    await expect(preview(page).locator('article')).toBeVisible();
    const response = await page.request.get(api);
    const responseBody = await response.text();
    const pending = new EventEmitter();
    await page.route(api, async (route) => {
      await once(pending, 'release');
      await route.fulfill({ status: 200, contentType: 'application/json', body: responseBody });
    });
    const requested = page.waitForRequest(api);
    await page.getByRole('button', { name: '関連情報を更新', exact: true }).click();
    await requested;
    await page.getByRole('button', { name: 'ログアウト', exact: true }).click();
    const delivered = page.waitForResponse(api);
    pending.emit('release');
    await delivered;
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator('iframe')).toHaveCount(0);
  });

  test('参照変更前の遅延応答を捨て、読込中に編集した入力も最新のまま表示する', async ({ page }) => {
    const articleId = await existingArticle();
    await openEditor(page, articleId);
    await openPreview(page);
    const api = `${stack.backendBaseUrl}/api/v1/admin/albums/*`;
    const pending = new EventEmitter();
    await page.route(api, async (route) => {
      const released = once(pending, 'release');
      const response = await route.fetch();
      await released;
      await route.fulfill({ response });
    });
    const requested = page.waitForRequest((request) =>
      request.url().includes('/api/v1/admin/albums/'),
    );
    await page.getByRole('button', { name: '関連情報を更新', exact: true }).click();
    await requested;
    await page.getByLabel('種別', { exact: true }).selectOption('NOTE');
    await page.getByLabel('タイトル', { exact: true }).fill('読込中の編集');
    await openPreview(page);
    const delivered = page.waitForResponse((response) =>
      response.url().includes('/api/v1/admin/albums/'),
    );
    pending.emit('release');
    await delivered;
    await expect(
      preview(page).getByRole('heading', { name: '読込中の編集', exact: true }),
    ).toBeVisible();
    await expect(preview(page).locator('[data-public-album]')).toHaveCount(0);
    await expect(page.getByLabel('種別', { exact: true })).toHaveValue('NOTE');
  });

  test('表示先を配信できないときも空白のプレビューで止まらない', async ({ page }) => {
    await page.route(`${stack.adminBaseUrl}/preview/article/`, (route) =>
      route.fulfill({
        status: 404,
        contentType: 'text/html',
        body: '<html><body>Not found</body></html>',
      }),
    );
    await openEditor(page);
    await expect(page.getByRole('alert')).toContainText('プレビューの画面を読み込めませんでした');
    await expect(page.locator('iframe')).toHaveCount(0);
  });

  test('認証が失効したときはプレビューを外し、入力を保持して鍵待ちへ戻る', async ({ page }) => {
    await openEditor(page, await existingArticle());
    const title = '失効前の未保存タイトル';
    await page.getByLabel('タイトル', { exact: true }).fill(title);
    await openPreview(page);
    await revokeBrowserSession(page);
    await page.getByRole('button', { name: '関連情報を更新', exact: true }).click();
    await expect(page.getByLabel('管理APIの鍵')).toBeVisible();
    await expect(page.locator('iframe')).toHaveCount(0);
    await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
    await page.getByRole('button', { name: '開く', exact: true }).click();
    await expect(page.getByLabel('タイトル', { exact: true })).toHaveValue(title);
  });
});
