import { findAlbumByCatalogNumber } from '../support/admin-api.ts';
import { showcase } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { expect, test } from '../support/fixtures.ts';

test('robots.txtは管理画面とAPIだけを検索対象から除外する', async ({ request }) => {
  const response = await request.get(`${stack.siteBaseUrl}/robots.txt`);

  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toBe('text/plain; charset=utf-8');
  expect((await response.text()).trim().split(/\r?\n/u)).toEqual([
    'User-agent: *',
    'Disallow: /admin',
    'Disallow: /api',
  ]);
});

test('CSPを強制しても公開表示・書体処理・SoundCloudフレーム・管理ログインが動く', async ({
  page,
}) => {
  const violations: string[] = [];
  page.on('console', (message) => {
    void (message.text().startsWith('CSP-VIOLATION') ? violations.push(message.text()) : undefined);
  });
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      console.error('CSP-VIOLATION', event.effectiveDirective, event.blockedURI);
    });
  });
  await page.route('https://w.soundcloud.com/**', (route) =>
    route.fulfill({
      contentType: 'text/html; charset=utf-8',
      body: '<!doctype html><p>埋め込み先の検査</p>',
    }),
  );
  const album = await findAlbumByCatalogNumber(showcase.catalogNumber);
  expect(album).toBeDefined();
  const response = await page.goto(`/albums/${album?.albumId ?? 'missing'}`);
  expect(response?.headers()['content-security-policy']).toContain("default-src 'none'");
  expect(response?.headers()['x-content-type-options']).toBe('nosniff');
  await expect(page.locator('html')).not.toHaveAttribute('data-fonts-loading', '');
  const player = page.locator('iframe').first();
  await player.scrollIntoViewIfNeeded();
  await expect(player.contentFrame().getByText('埋め込み先の検査')).toBeVisible();

  await page.goto(`${stack.adminBaseUrl}/`);
  await page.getByLabel('管理APIの鍵').fill(stack.adminApiKey);
  await page.getByRole('button', { name: '開く', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: showcase.title })).toBeVisible();
  expect(violations).toEqual([]);
});

test('CSPが任意の外部スクリプト・通信・フレームとevalを拒否する', async ({ page }) => {
  const violations: string[] = [];
  page.on('console', (message) => {
    void (message.text().startsWith('CSP-VIOLATION') ? violations.push(message.text()) : undefined);
  });
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      console.error('CSP-VIOLATION', event.effectiveDirective);
    });
  });
  /* DevToolsの評価コンテキストを離れ、ページが取得したスクリプトとしてevalを検査する。 */
  await page.route('**/csp-eval-probe.js', (route) =>
    route.fulfill({ contentType: 'text/javascript', body: "eval('1 + 1')" }),
  );
  await page.goto('/');
  await page.evaluate(() => {
    const script = document.createElement('script');
    script.src = 'https://blocked.example.invalid/script.js';
    document.body.append(script);
    const frame = document.createElement('iframe');
    frame.src = 'https://blocked.example.invalid/';
    document.body.append(frame);
    void fetch('https://blocked.example.invalid/data').catch(() => undefined);
    /* CSPのeval拒否を実ブラウザで確認するため、検査用の式を実行する。 */
    const evaluation = document.createElement('script');
    evaluation.src = '/csp-eval-probe.js';
    document.body.append(evaluation);
    const button = document.createElement('button');
    button.setAttribute('onclick', 'window.inlineHandlerExecuted = true');
    document.body.append(button);
    button.click();
  });
  await expect.poll(() => violations).toContain('CSP-VIOLATION script-src-elem');
  await expect.poll(() => violations).toContain('CSP-VIOLATION script-src');
  await expect.poll(() => violations).toContain('CSP-VIOLATION script-src-attr');
  await expect.poll(() => violations).toContain('CSP-VIOLATION connect-src');
  await expect.poll(() => violations).toContain('CSP-VIOLATION frame-src');
});
