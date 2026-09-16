import { stack } from '../support/config.ts';
import { capture, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

test('公開・管理の未存在ページは404本文を返し、HEADも404になる', async ({ page, request }) => {
  const missing = `${stack.siteBaseUrl}/missing-page`;
  const publicResponse = await page.goto(missing);
  expect(publicResponse?.status()).toBe(404);
  await expect(
    page.getByRole('heading', { level: 1, name: 'ページが見つかりません' }),
  ).toBeVisible();
  await capture(page, '404-public');
  await clickWithEvidence(
    page,
    page.getByRole('link', { name: 'トップへ戻る' }),
    '404-public-return',
  );
  await expect(page).toHaveURL(`${stack.siteBaseUrl}/`);

  const adminMissing = `${stack.adminBaseUrl}/missing-page`;
  const adminResponse = await page.goto(adminMissing);
  expect(adminResponse?.status()).toBe(404);
  await expect(
    page.getByRole('heading', { level: 1, name: 'ページが見つかりません' }),
  ).toBeVisible();
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, nofollow');
  await capture(page, '404-admin');
  await clickWithEvidence(
    page,
    page.getByRole('link', { name: '作品の一覧へ戻る' }),
    '404-admin-return',
  );
  await expect(page).toHaveURL(`${stack.adminBaseUrl}/`);

  const get = await request.get(adminMissing);
  const head = await request.head(adminMissing);
  expect(head.status()).toBe(404);
  expect(await head.body()).toHaveLength(0);
  expect(head.headers()['content-length']).toBe(get.headers()['content-length']);
  expect(head.headers()['cache-control']).toBe('no-store');
});

test('欠落したスクリプトとAPIのProblem DetailsはページHTMLに変わらない', async ({ request }) => {
  const script = await request.get(`${stack.adminBaseUrl}/_astro/missing.js`);
  expect(script.status()).toBe(404);
  expect(script.headers()['content-type']).not.toContain('text/html');
  const asset = await request.get(`${stack.siteBaseUrl}/assets/missing.png`);
  expect(asset.ok()).toBe(false);
  expect(asset.headers()['content-type'] ?? '').not.toContain('text/html');
  /* Local E2E has separate origins. Edge unit/Terraform tests cover the behavior binding. */
  const api = await request.get(
    `${stack.backendBaseUrl}/api/v1/albums/00000000-0000-4000-8000-000000000000`,
  );
  expect(api.status()).toBe(404);
  expect(api.headers()['content-type']).toContain('application/problem+json');
  expect(await api.json()).toMatchObject({ status: 404 });
});
