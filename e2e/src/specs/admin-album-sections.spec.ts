import type { Locator, Page } from '@playwright/test';

import { openSection } from '../support/album-editor.ts';
import { stack } from '../support/config.ts';
import { capture, captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbum } from '../support/scratch-albums.ts';

/**
 * 管理画面の作品の編集を、区画ごとに畳む（#122）。
 *
 * <p>
 * <b>既定はどの区画も畳んでいる。</b> 作品1件の入力は縦に長く、全部が開いたままでは、いま何を編集して
 * いるのかを見失う。畳んだ区画は公開サイトと同じ読み方の要約で並び、<b>畳み切った画面はその作品の姿</b>
 * になる。開いた区画だけが入力に変わる。
 * </p>
 *
 * <p>
 * <b>断られた区画は畳めない。</b> 理由は欄の下に出るため、畳んだままでは直す先が画面から消える。
 * </p>
 *
 * 文言は画面の実装が持つため、シナリオ側に置く。
 */

/** 鍵の入力欄と、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 一覧に置く導線 */
const EDIT_LABEL = '編集する';
const NEW_LABEL = '作品を追加する';

/** 入力欄のラベル */
const TITLE_LABEL = 'タイトル';
const URL_LABEL = '音源のURL';

/** 保存の操作 */
const CREATE_LABEL = '作成する';

/** 幅。導線の切り替え（#357）と同じ2点で見る */
const WIDE = { width: 1280, height: 800 };
const NARROW = { width: 390, height: 844 };

/** 鍵を入れて一覧が出た状態にする */
const openAdmin = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

/** 一覧から対象の編集を開く。区画は畳まれたままにする（それがこのシナリオの見どころ） */
const openEdit = async (page: Page, title: string): Promise<void> => {
  await page
    .getByRole('row')
    .filter({ hasText: title })
    .getByRole('link', { name: EDIT_LABEL })
    .click();
  await expect(page.getByRole('button', { name: '作品を開く' })).toBeVisible();
};

const openAlbumFor = async (page: Page, purpose: string): Promise<string> => {
  const title = await seedScratchAlbum(purpose);

  await openAdmin(page);
  await openEdit(page, title);

  return title;
};

/** 区画と、畳んだときにそこへ出る要約 */
const section = (page: Page, heading: string): Locator =>
  page.locator(`[data-section="${heading}"]`);
const summaryOf = (page: Page, heading: string): Locator =>
  section(page, heading).locator('[data-section-summary]');

test.afterEach(deleteScratchAlbums);

test.describe('管理画面の作品の区画', () => {
  test('開いた直後はどの区画も畳まれ、入っているものが要約で並ぶ', async ({ page }) => {
    const title = await openAlbumFor(page, '区画の要約');

    await expect(summaryOf(page, '作品')).toContainText(title);

    /* 開くまで欄は描かれない。畳んだ姿が読む形であることが、この区画を持つ理由である */
    await expect(page.getByLabel(TITLE_LABEL)).toHaveCount(0);
    await expect(page.getByLabel(URL_LABEL)).toHaveCount(0);

    await captureFocused(page, section(page, '作品'), '22a-admin-edit-collapsed');
  });

  test('何も入っていない区画は、無いことを示す', async ({ page }) => {
    await openAlbumFor(page, '区画の空');

    await expect(summaryOf(page, '曲目')).toHaveText('（なし）');
    await expect(summaryOf(page, '外部音源')).toHaveText('（なし）');
    await expect(summaryOf(page, '初出イベント')).toHaveText('（未入力）');
  });

  test('開いた区画だけが入力に変わり、他は畳まれたまま', async ({ page }) => {
    const title = await openAlbumFor(page, '区画を開く');

    await openSection(page, '作品');

    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
    await expect(page.getByLabel(URL_LABEL)).toHaveCount(0);

    /* 開いた区画の要約は入力と入れ替わりに消える。畳んだ区画は要約のまま残る */
    await expect(summaryOf(page, '作品')).toHaveCount(0);
    await expect(summaryOf(page, '外部音源')).toBeVisible();

    await captureFocused(page, section(page, '作品'), '22b-admin-edit-section-open');
  });

  test('畳み直すと、書きかけのまま要約へ戻る', async ({ page }) => {
    await openAlbumFor(page, '区画を畳み直す');

    await openSection(page, '作品');
    await page.getByLabel(TITLE_LABEL).fill('E2E 書きかけのタイトル');

    await page.getByRole('button', { name: '作品を畳む' }).click();

    /*
     * KEEP-WHILE-COLLAPSED: 入力は画面の下書きが持ち、区画が持つのは見え方だけである。要約が書きかけを
     * 映していれば、畳んでも入力が生きていることが読める。
     */
    await expect(summaryOf(page, '作品')).toContainText('E2E 書きかけのタイトル');

    await openSection(page, '作品');
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue('E2E 書きかけのタイトル');
  });

  /*
   * ACTIONS-ESCAPE-TO-THE-SIDE: 保存の操作は画面の下端に留まる。広い幅では本文の右へ逃がす——本文に
   * 重なると、いちばん下の欄が操作の裏に隠れる。重なりは検査が落ちない欠陥なので、位置そのものを見る。
   */
  test('広い幅では、下端の操作が本文に重ならない', async ({ page }) => {
    await page.setViewportSize(WIDE);
    await openAlbumFor(page, '操作の位置');
    await openSection(page, '作品');

    const actionsBox = await page.locator('[data-album-actions]').boundingBox();
    const fieldBox = await page.getByLabel(TITLE_LABEL).boundingBox();

    /* 本文の右端より右から始まっていること */
    expect(actionsBox?.x).toBeGreaterThanOrEqual((fieldBox?.x ?? 0) + (fieldBox?.width ?? 0));

    await captureFocused(page, page.locator('[data-album-actions]'), '22d-admin-edit-actions-wide');
  });

  test('狭い幅では、下端の操作が画面の下に留まる', async ({ page }) => {
    await page.setViewportSize(NARROW);
    await openAlbumFor(page, '狭い幅の操作');

    const actionsBox = await page.locator('[data-album-actions]').boundingBox();

    /* 画面の下端にあること。スクロールしても付いてくる（`fixed`）ため、位置は viewport で決まる */
    expect(actionsBox?.y).toBeGreaterThan(NARROW.height / 2);

    await capture(page, '22e-admin-edit-actions-narrow');
  });

  /*
   * REJECTED-SECTION-OPENS: 理由は欄の下に出る。断られた区画が畳まれたままだと、直す先が画面から
   * 消える。新規作成は必須を入れずに送れるため、断られる経路をそのまま使える。
   */
  test('断られた区画は、畳んだままでも開く', async ({ page }) => {
    await openAdmin(page);
    await page.getByRole('link', { name: NEW_LABEL }).click();

    await expect(page.getByLabel(TITLE_LABEL)).toHaveCount(0);

    await page.getByRole('button', { name: CREATE_LABEL }).click();

    await expect(page.locator('[data-field="title"]').getByRole('alert')).toBeVisible();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveAttribute('aria-invalid', 'true');

    /* 断りに関係しない区画は畳まれたまま。全部を開くと、どこを直すのかが読めなくなる */
    await expect(page.getByLabel(URL_LABEL)).toHaveCount(0);

    await capture(page, '22c-admin-edit-rejected-section');
  });
});
