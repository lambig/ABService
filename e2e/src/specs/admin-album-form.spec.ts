import type { Locator, Page } from '@playwright/test';

import { stack } from '../support/config.ts';
import { capture, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import {
  SCRATCH_CATALOG_PREFIX,
  deleteScratchAlbums,
  seedScratchAlbum,
} from '../support/scratch-albums.ts';

/**
 * 管理画面の作品の新規作成・編集（#288 / #122）のジャーニー。
 *
 * <p>
 * 見ているのは、実APIが返した 400 の位置（`field`）が各入力欄へ割り当てられることである。応答を
 * 差し替えず、実際に不正な入力を送って返ってきたものを描く（#164 の「APIのモックはしない」）。
 * </p>
 *
 * 文言は画面の実装が持つため、シナリオ側に置く。
 */

/** 鍵の入力欄と、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 一覧に置く導線 */
const NEW_LABEL = '作品を追加する';
const EDIT_LABEL = '編集する';

/** 入力欄のラベル */
const TITLE_LABEL = 'タイトル';
const RELEASE_DATE_LABEL = 'リリース日';
const ARTIST_LABEL = 'アーティスト表示名';
const CATALOG_NUMBER_LABEL = 'カタログナンバー';
const ISDN_LABEL = 'ISDN';
const EVENT_NAME_LABEL = 'イベント名';
const EVENT_PLACE_LABEL = '会場';

/** 保存の操作 */
const SAVE_LABEL = '保存する';
const CREATE_LABEL = '作成する';

/** 未公開であることを示すラベル */
const DRAFT_LABEL = '下書き';

/** 更新の経路。到達できない状態を作るために塞ぐ */
const UPDATE_API = `${stack.backendBaseUrl}/api/v1/albums/*`;

/** 鍵を入れて一覧が出た状態にする */
const openAdmin = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

/** タイトルで一覧の行を指す */
const rowOf = (page: Page, title: string): Locator =>
  page.getByRole('row').filter({ hasText: title });

/**
 * 欄ごとのまとまりを指す。
 *
 * 位置の綴り（管理APIが `field` として返す入力パス）を属性に持たせている。エラーが**その欄の下**に
 * 出ていることを見るため、ラベルではなくまとまりを経由する。
 */
const fieldOf = (page: Page, path: string): Locator => page.locator(`[data-field="${path}"]`);

/** 一覧から対象の編集を開く */
const openEdit = async (page: Page, title: string): Promise<void> => {
  await rowOf(page, title).getByRole('link', { name: EDIT_LABEL }).click();
  await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
};

test.afterEach(deleteScratchAlbums);

test.describe('管理画面の作品の編集', () => {
  test('一覧から編集を開くと、登録されている値が入っている', async ({ page }) => {
    const title = await seedScratchAlbum('編集');

    await openAdmin(page);
    await clickWithEvidence(
      page,
      rowOf(page, title).getByRole('link', { name: EDIT_LABEL }),
      '22-admin-edit-open',
    );

    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
    await expect(page.getByLabel(RELEASE_DATE_LABEL)).toHaveValue('2026-09-01');
    await expect(page.getByLabel(CATALOG_NUMBER_LABEL)).toHaveValue(
      new RegExp(`^${SCRATCH_CATALOG_PREFIX}`, 'u'),
    );
    await capture(page, '23-admin-edit-loaded');
  });

  test('検証エラーは、応答が返した位置のとおりに各欄へ出る', async ({ page }) => {
    const title = await seedScratchAlbum('検証エラー');

    await openAdmin(page);
    await openEdit(page, title);

    /*
     * 4か所を同時に不正にする。必須（title / releaseDate）、形式（isdn）、入れ子（event.name。
     * 会場だけを入れるとイベント名が要る）で、位置の種類が違うものを混ぜる。
     */
    await page.getByLabel(TITLE_LABEL).fill('   ');
    await page.getByLabel(RELEASE_DATE_LABEL).fill('');
    await page.getByLabel(ISDN_LABEL).fill('not-an-isdn');
    await page.getByLabel(EVENT_PLACE_LABEL).fill('E2E 会場');
    await page.getByLabel(EVENT_NAME_LABEL).fill('');

    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '24-admin-edit-invalid-submit',
    );

    /* 位置ごとに、その欄のまとまりの中へ出ていること */
    await expect(fieldOf(page, 'title').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'releaseDate').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'isdn').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'event.name').getByRole('alert')).toBeVisible();

    /* 不正でない欄へは出さない（位置を捨てて全体へ寄せていないこと） */
    await expect(fieldOf(page, 'artistDisplayName').getByRole('alert')).toHaveCount(0);
    await expect(fieldOf(page, 'catalogNumber').getByRole('alert')).toHaveCount(0);

    await expect(page.getByLabel(TITLE_LABEL)).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByLabel(ARTIST_LABEL)).toHaveAttribute('aria-invalid', 'false');
    await capture(page, '25-admin-edit-field-errors');
  });

  test('直して保存すると、一覧に反映される', async ({ page }) => {
    const title = await seedScratchAlbum('保存');
    const renamed = `${title} 改題`;

    await openAdmin(page);
    await openEdit(page, title);

    await page.getByLabel(TITLE_LABEL).fill('   ');
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(fieldOf(page, 'title').getByRole('alert')).toBeVisible();

    /* 同じ画面のまま直して送り直せること（入力をやり直させない） */
    await page.getByLabel(TITLE_LABEL).fill(renamed);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '26-admin-edit-save',
    );

    await expect(page.getByRole('table')).toBeVisible();
    await expect(rowOf(page, renamed)).toBeVisible();
    await capture(page, '27-admin-edit-saved');
  });

  test('保存に到達できないときは、入力を保ったまま理由を出す', async ({ page }) => {
    const title = await seedScratchAlbum('保存失敗');
    const renamed = `${title} 未保存`;

    await openAdmin(page);
    await openEdit(page, title);
    await page.getByLabel(TITLE_LABEL).fill(renamed);

    /* 応答を差し替えるのではなくネットワークの側で塞ぐ */
    await page.route(UPDATE_API, (route) => route.abort());
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(renamed);
    await capture(page, '28-admin-edit-unreachable');

    /* 検証エラーではないため、欄には出さない */
    await expect(fieldOf(page, 'title').getByRole('alert')).toHaveCount(0);
    await page.unroute(UPDATE_API);
  });

  test('対象を指定せずに編集を開くと、対象が無いと言う', async ({ page }) => {
    await page.goto(`${stack.adminBaseUrl}/albums/edit`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await expect(page.getByRole('alert')).toContainText('編集する作品が指定されていません');
    await expect(page.getByLabel(TITLE_LABEL)).toHaveCount(0);
  });
});

test.describe('管理画面の作品の追加', () => {
  test('必須を入れて作成すると、下書きとして一覧に並ぶ', async ({ page }) => {
    const stamp = String(Date.now());
    const title = `E2E 画面から追加した作品 ${stamp}`;

    await openAdmin(page);
    await clickWithEvidence(page, page.getByRole('link', { name: NEW_LABEL }), '29-admin-new-open');

    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByLabel(RELEASE_DATE_LABEL).fill('2026-10-01');
    await page.getByLabel(ARTIST_LABEL).fill('E2E 追加アーティスト');
    /* 片付けの対象に入るよう、控えではなくカタログナンバーの接頭辞で拾えるようにする */
    await page.getByLabel(CATALOG_NUMBER_LABEL).fill(`${SCRATCH_CATALOG_PREFIX}${stamp}`);

    await clickWithEvidence(
      page,
      page.getByRole('button', { name: CREATE_LABEL }),
      '30-admin-new-create',
    );

    await expect(page.getByRole('table')).toBeVisible();
    await expect(rowOf(page, title)).toContainText(DRAFT_LABEL);
    await capture(page, '31-admin-new-created');
  });

  test('必須を入れずに作成すると、その欄にエラーが出る', async ({ page }) => {
    await openAdmin(page);
    await page.getByRole('link', { name: NEW_LABEL }).click();

    await page.getByRole('button', { name: CREATE_LABEL }).click();

    await expect(fieldOf(page, 'title').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'releaseDate').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'artistDisplayName').getByRole('alert')).toBeVisible();
  });
});
