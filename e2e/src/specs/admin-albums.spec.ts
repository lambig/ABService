import { draft, showcase } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 管理画面の作品一覧（#122）のジャーニー。
 *
 * 公開サイトと違い、画面の中身はブラウザが管理APIから引く（下書きを含むため、組み立ての時点の内容を
 * 配れない）。したがってここで見ているのは、組み上がった HTML ではなく実際の往復である。
 *
 * 文言は画面の実装が持つため、シナリオ側に置く。
 */

/** 鍵の入力欄のラベル */
const API_KEY_LABEL = '管理APIの鍵';

/** 鍵を送る操作 */
const OPEN_LABEL = '開く';

/** 未公開であることを示すラベル */
const DRAFT_LABEL = '下書き';

test.describe('管理画面の作品一覧', () => {
  test('鍵を入れるまで作品を出さない', async ({ page }) => {
    await page.goto(stack.adminBaseUrl);

    await expect(page.getByLabel(API_KEY_LABEL)).toBeVisible();
    await expect(page.getByText(showcase.title)).toHaveCount(0);
    await capture(page, '14-admin-locked');
  });

  test('鍵を入れると、下書きを含む作品が並ぶ', async ({ page }) => {
    await page.goto(stack.adminBaseUrl);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);

    await clickWithEvidence(page, page.getByRole('button', { name: OPEN_LABEL }), '15-admin-open');

    await expect(page.getByRole('row').filter({ hasText: showcase.title })).toBeVisible();

    /*
     * 下書きは公開サイトのどこにも出ない（#197）。管理画面はそれを状態とともに並べる。ここが
     * 公開向けと管理向けで応答が違うことの確認になる。
     */
    await expect(page.getByRole('row').filter({ hasText: draft.title })).toContainText(DRAFT_LABEL);
    await capture(page, '16-admin-albums');
  });

  test('受け付けられない鍵は断る', async ({ page }) => {
    await page.goto(stack.adminBaseUrl);
    await page.getByLabel(API_KEY_LABEL).fill('e2e-wrong-key');
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText(showcase.title)).toHaveCount(0);
  });
});
