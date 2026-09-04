import type { Locator, Page } from '@playwright/test';

import { seedDraftAlbum } from '../support/admin-api.ts';
import { albumArticle, draft, showcase } from '../support/build-fixtures.ts';
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

/** 一覧に置く操作 */
const DELETE_LABEL = '削除する';
const UNPUBLISH_LABEL = '非公開にする';
const PUBLISH_LABEL = '公開する';

/** 公開中であることを示すラベル */
const PUBLISHED_LABEL = '公開';

/** 確認の対話 */
const DELETE_DIALOG_TITLE = 'この作品を削除しますか';
const UNPUBLISH_DIALOG_TITLE = 'この作品を非公開にしますか';
const CANCEL_LABEL = 'やめる';
const UNPUBLISH_AFFECTED_HEADING = '連動して非公開になる記事';
const NO_AFFECTED_TEXT = '影響を受けるものはありません。';

/** 鍵を入れて一覧が出た状態にする */
const openAdmin = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

/** タイトルで一覧の行を指す。操作ボタンは行ごとに並ぶため、行を経由して押す */
const rowOf = (page: Page, title: string): Locator =>
  page.getByRole('row').filter({ hasText: title });

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

  test('非公開の事前確認は、連動して非公開になる記事を出す', async ({ page }) => {
    await openAdmin(page);

    /*
     * 影響範囲は照会（#274）が返したものを出す。画面は参照元の一覧から「どれが非公開になるか」を
     * 組み立て直さない。シードした作品は公開中の記事から参照されているため、その記事が並ぶ。
     */
    await clickWithEvidence(
      page,
      rowOf(page, showcase.title).getByRole('button', { name: UNPUBLISH_LABEL }),
      '18-admin-unpublish-confirm',
    );

    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(UNPUBLISH_DIALOG_TITLE);
    await expect(dialog).toContainText(UNPUBLISH_AFFECTED_HEADING);
    await expect(dialog).toContainText(albumArticle.title);
    await capture(page, '19-admin-unpublish-affected');

    /* やめれば何も起きない。確認は実行と別の操作である */
    await dialog.getByRole('button', { name: CANCEL_LABEL }).click();
    await expect(dialog).toHaveCount(0);
    await expect(
      rowOf(page, showcase.title).getByRole('button', { name: UNPUBLISH_LABEL }),
    ).toBeVisible();
  });

  test('参照されていない作品の削除は、影響なしとして確認できる', async ({ page }) => {
    const title = `E2E 削除確認アルバム ${String(Date.now())}`;
    await seedDraftAlbum({
      title,
      releaseDate: '2026-09-01',
      artistDisplayName: 'E2E 削除確認アーティスト',
      artistSortKey: 'E2E DELETE',
      catalogNumber: `E2E-DEL-${String(Date.now())}`,
    });

    await openAdmin(page);
    await rowOf(page, title).getByRole('button', { name: DELETE_LABEL }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(DELETE_DIALOG_TITLE);
    await expect(dialog).toContainText(NO_AFFECTED_TEXT);

    /* 確定すると一覧から消える。消えたことを一覧の読み直しで見る（画面側で行を隠すのではない） */
    await clickWithEvidence(
      page,
      dialog.getByRole('button', { name: DELETE_LABEL }),
      '20-admin-delete-confirm',
    );

    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(rowOf(page, title)).toHaveCount(0);
  });

  test('下書きは公開でき、公開すると状態が変わる', async ({ page }) => {
    const title = `E2E 公開アルバム ${String(Date.now())}`;
    await seedDraftAlbum({
      title,
      releaseDate: '2026-09-02',
      artistDisplayName: 'E2E 公開アーティスト',
      artistSortKey: 'E2E PUBLISH',
      catalogNumber: `E2E-PUB-${String(Date.now())}`,
    });

    await openAdmin(page);
    await expect(rowOf(page, title)).toContainText(DRAFT_LABEL);

    /* 公開は影響を及ばせないため確認を挟まない（#274 が前提を持つのは削除と非公開化） */
    await rowOf(page, title).getByRole('button', { name: PUBLISH_LABEL }).click();

    await expect(rowOf(page, title)).toContainText(PUBLISHED_LABEL);
    await expect(rowOf(page, title).getByRole('button', { name: UNPUBLISH_LABEL })).toBeVisible();
  });
});
