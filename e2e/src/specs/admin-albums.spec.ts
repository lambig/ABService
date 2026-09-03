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

/** 失敗したときの復帰の操作 */
const RETRY_LABEL = '再試行';
const DISCARD_LABEL = '鍵を破棄する';

/** 管理APIの経路。到達できない状態を作るために塞ぐ */
const ADMIN_API = `${stack.backendBaseUrl}/api/v1/admin/**`;

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

  test('管理APIへ到達できないときは、同じ鍵でやり直せる', async ({ page }) => {
    await page.goto(stack.adminBaseUrl);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);

    /*
     * 到達できない状態を作る。応答を差し替えるのではなくネットワークの側で塞ぐ（#164 の「APIの
     * モックはしない」に沿う）。読み込み中のまま止まらないこと、入力からやり直さずに済むことを見る。
     */
    await page.route(ADMIN_API, (route) => route.abort());
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('button', { name: DISCARD_LABEL })).toBeVisible();

    await page.unroute(ADMIN_API);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: RETRY_LABEL }),
      '17-admin-retry',
    );

    await expect(page.getByRole('row').filter({ hasText: showcase.title })).toBeVisible();
  });

  test('受け付けられない鍵は断る', async ({ page }) => {
    await page.goto(stack.adminBaseUrl);
    await page.getByLabel(API_KEY_LABEL).fill('e2e-wrong-key');
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText(showcase.title)).toHaveCount(0);
  });
});
