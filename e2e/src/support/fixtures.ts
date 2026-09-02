import { test as base } from '@playwright/test';

/**
 * 全シナリオ共通の前提。
 *
 * 外部サービスへの依存はネットワークで遮断する（#164）。埋め込みプレイヤーを実際に読み込むと、CI の
 * 成否が外部サービスの可用性に左右される。遮断したうえで、`src` の組み立てとフォールバックの表示を見る。
 */
export const test = base.extend({
  page: async ({ page }, use) => {
    await page.route('**://*.soundcloud.com/**', (route) => route.abort());
    await use(page);
  },
});

export { expect } from '@playwright/test';
