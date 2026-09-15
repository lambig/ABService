import type { Page } from '@playwright/test';
import { stack } from './config.ts';
import { expect } from './fixtures.ts';

/** ブラウザーの認証情報は残したまま実サーバーで失効させ、次の操作での再認証を検査する。 */
export const revokeBrowserSession = async (page: Page): Promise<void> => {
  const token = await page.evaluate(() => {
    const value = JSON.parse(sessionStorage.getItem('abservice.admin.session') ?? 'null') as {
      token: string;
    } | null;
    return value?.token ?? '';
  });
  const response = await page.request.delete(
    `${stack.backendBaseUrl}/api/v1/admin/sessions/current`,
    {
      headers: { Authorization: `Bearer ${token}` },
    },
  );
  expect(response.status()).toBe(204);
};
