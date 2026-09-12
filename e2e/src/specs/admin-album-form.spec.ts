import { setTimeout as delay } from 'node:timers/promises';

import type { Locator, Page } from '@playwright/test';

import { renameAlbumOutsideTheScreen } from '../support/admin-api.ts';
import { stack } from '../support/config.ts';
import { capture, clickWithEvidence, focusOn } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import {
  SCRATCH_BASE_PRICE,
  SCRATCH_CATALOG_PREFIX,
  deleteScratchAlbums,
  seedScratchAlbum,
  seedScratchAlbumDetail,
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
const BASE_PRICE_LABEL = '基準額';
const CURRENCY_LABEL = '通貨コード（未指定は円）';
const ORIGINAL_WORK_NOTE_LABEL = '原作の出典（例:「○○」より各曲）';

/** 頒布のまとまりを外す操作 */
const CLEAR_BASE_PRICE_LABEL = '基準額を解除';

/** 保存の操作 */
const SAVE_LABEL = '保存する';
const CREATE_LABEL = '作成する';

/** 未公開であることを示すラベル */
const DRAFT_LABEL = '下書き';

/** 更新の経路。到達できない状態・応答が遅い状態を作るために塞ぐ */
const UPDATE_API = `${stack.backendBaseUrl}/api/v1/albums/*`;

/** 保存の応答を遅らせる時間。保存中の状態を観測する余地を作る */
const SLOW_SAVE_MS = 2_000;

/** 受け付けられない鍵。断られた後の復帰を見るために使う */
const WRONG_API_KEY = 'e2e-wrong-key';

/** 入力を抱えたまま鍵待ちへ戻ったことを伝える文言 */
const PENDING_NOTICE = '入力した内容は保持しています';

/** 編集を始めた後に別の操作が保存していたことを伝える見出しと、その復帰の操作 */
const CONFLICT_HEADING = '編集を始めた後に、別の操作がこの作品を保存しています';
const RELOAD_LABEL = '最新を読み込む';

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
    await expect(page.getByLabel(BASE_PRICE_LABEL)).toHaveValue(String(SCRATCH_BASE_PRICE));
    await capture(page, '23-admin-edit-loaded');
  });

  test('基準額を変えて保存すると、読み直した編集にその額が入っている', async ({ page }) => {
    const title = await seedScratchAlbum('基準額');
    const raised = String(SCRATCH_BASE_PRICE + 300);

    await openAdmin(page);
    await openEdit(page, title);

    await page.getByLabel(BASE_PRICE_LABEL).fill(raised);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '38-admin-edit-base-price',
    );

    await expect(page.getByRole('table')).toBeVisible();

    /* 保存できたことは、保存後の値を読み直して確かめる（一覧は額を出さない） */
    await openEdit(page, title);
    await expect(page.getByLabel(BASE_PRICE_LABEL)).toHaveValue(raised);
    await capture(page, '39-admin-edit-base-price-saved');
  });

  test('基準額を解除して保存すると、読み直した編集で額を持たない', async ({ page }) => {
    const title = await seedScratchAlbum('基準額の解除');

    await openAdmin(page);
    await openEdit(page, title);
    await expect(page.getByLabel(BASE_PRICE_LABEL)).toHaveValue(String(SCRATCH_BASE_PRICE));

    /*
     * 額の欄だけを空にすると通貨が残り、まとまりごと送られて額の必須で断られる。外す操作はその規則を
     * 利用者に求めないために置いている（#352 のレビュー）。
     */
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: CLEAR_BASE_PRICE_LABEL }),
      '39a-admin-edit-base-price-clear',
    );

    await expect(page.getByLabel(BASE_PRICE_LABEL)).toHaveValue('');
    await expect(page.getByLabel(CURRENCY_LABEL)).toHaveValue('');

    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    /* 保存できたことは、保存後の値を読み直して確かめる（一覧は額を出さない） */
    await openEdit(page, title);
    await expect(page.getByLabel(BASE_PRICE_LABEL)).toHaveValue('');
    await expect(page.getByLabel(CURRENCY_LABEL)).toHaveValue('');
    await capture(page, '39b-admin-edit-base-price-cleared');
  });

  test('原作の出典を入れて保存すると、読み直した編集に同じ綴りが入っている', async ({ page }) => {
    const title = await seedScratchAlbum('原作の出典');
    const note = '「E2E 管理原作」より各曲';

    await openAdmin(page);
    await openEdit(page, title);

    /* 作品は記述を持たない状態で作られる。空欄から入れて、往復で綴りが変わらないことを見る（#365） */
    await expect(page.getByLabel(ORIGINAL_WORK_NOTE_LABEL)).toHaveValue('');

    await page.getByLabel(ORIGINAL_WORK_NOTE_LABEL).fill(note);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '39c-admin-edit-original-work-note',
    );

    await expect(page.getByRole('table')).toBeVisible();

    /* 保存できたことは、保存後の値を読み直して確かめる（一覧は記述を出さない） */
    await openEdit(page, title);
    await expect(page.getByLabel(ORIGINAL_WORK_NOTE_LABEL)).toHaveValue(note);

    /* 欄は画面の下の方にある。寄せてから撮らないと、証跡に欄そのものが写らない（#359） */
    await focusOn(page.getByLabel(ORIGINAL_WORK_NOTE_LABEL));
    await capture(page, '39d-admin-edit-original-work-note-saved');
  });

  test('額の欄だけを空にした保存は、額が必須として断られる', async ({ page }) => {
    const title = await seedScratchAlbum('額だけ空');

    await openAdmin(page);
    await openEdit(page, title);

    await page.getByLabel(BASE_PRICE_LABEL).fill('');
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    /* 通貨が残る限り頒布のまとまりは送られる。捨てずにエラーとして返す（黙って消さない） */
    await expect(fieldOf(page, 'basePrice.amount').getByRole('alert')).toBeVisible();
    await expect(page.getByLabel(CURRENCY_LABEL)).toHaveValue('JPY');
  });

  test('額が負なら、その欄にエラーが出る', async ({ page }) => {
    const title = await seedScratchAlbum('負の額');

    await openAdmin(page);
    await openEdit(page, title);

    await page.getByLabel(BASE_PRICE_LABEL).fill('-1');
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(fieldOf(page, 'basePrice.amount').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'title').getByRole('alert')).toHaveCount(0);
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

  test('保存中は入力も塞ぐ（送信後の変更が黙って消えない）', async ({ page }) => {
    const title = await seedScratchAlbum('保存中');

    await openAdmin(page);
    await openEdit(page, title);
    await page.getByLabel(TITLE_LABEL).fill(`${title} 保存中`);

    /* 応答を遅らせて、保存中の状態を観測できるようにする（要求そのものは通す） */
    await page.route(UPDATE_API, async (route) => {
      await delay(SLOW_SAVE_MS);
      await route.continue();
    });
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(page.getByLabel(TITLE_LABEL)).toBeDisabled();
    await expect(page.getByLabel(EVENT_PLACE_LABEL)).toBeDisabled();
    await capture(page, '32-admin-edit-saving');

    /* 応答が返れば保存は成立し、一覧へ戻る */
    await expect(page.getByRole('table')).toBeVisible();
    await page.unroute(UPDATE_API);
  });

  test('編集中に別の操作が保存していたら、入力を保ったまま競合を伝える', async ({ page }) => {
    const album = await seedScratchAlbumDetail('競合');
    const renamed = `${album.title} 画面からの改題`;
    const savedElsewhere = `${album.title} 別タブの保存`;

    await openAdmin(page);
    await openEdit(page, album.title);
    await page.getByLabel(TITLE_LABEL).fill(renamed);

    /*
     * 画面が読んだ後に、別のタブ（ここではAPI経由）が同じ作品を保存する。画面はこの時点の世代を持って
     * いないため、そのまま保存すると別タブの保存を消すことになる。
     */
    await renameAlbumOutsideTheScreen(album.albumId, savedElsewhere);

    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '35-admin-edit-conflict-submit',
    );

    /* 競合として伝え、入力は保つ（欄のエラーとしては出さない） */
    await expect(page.getByText(CONFLICT_HEADING)).toBeVisible();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(renamed);
    await expect(fieldOf(page, 'title').getByRole('alert')).toHaveCount(0);
    await capture(page, '36-admin-edit-conflicted');

    /* 別タブの保存は消えていない */
    await expect(page.getByRole('table')).toHaveCount(0);

    /* 最新を読み込むと、保存されている内容に置き換わる */
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: RELOAD_LABEL }),
      '37-admin-edit-conflict-reload',
    );

    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(savedElsewhere);
    await expect(page.getByText(CONFLICT_HEADING)).toHaveCount(0);

    /* 読み直した後の世代なら保存できる */
    await page.getByLabel(TITLE_LABEL).fill(renamed);
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();
    await expect(rowOf(page, renamed)).toBeVisible();
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

  test('鍵が断られても入力は残り、入れ直せば続けて作成できる', async ({ page }) => {
    const stamp = String(Date.now());
    const title = `E2E 鍵を入れ直して追加した作品 ${stamp}`;

    /*
     * 新規作成は鍵の入力時に管理APIを呼ばない（読み込むものが無い）。したがって鍵が正しいと分かるのは
     * 最初の保存のときで、そこで入力を捨てると全項目を書き直させることになる。
     */
    await page.goto(`${stack.adminBaseUrl}/albums/new`);
    await page.getByLabel(API_KEY_LABEL).fill(WRONG_API_KEY);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByLabel(RELEASE_DATE_LABEL).fill('2026-11-01');
    await page.getByLabel(ARTIST_LABEL).fill('E2E 再認証アーティスト');
    await page.getByLabel(CATALOG_NUMBER_LABEL).fill(`${SCRATCH_CATALOG_PREFIX}${stamp}`);

    await page.getByRole('button', { name: CREATE_LABEL }).click();

    /* 鍵の入力へ戻るが、入力を抱えていることを伝える */
    await expect(page.getByLabel(API_KEY_LABEL)).toBeVisible();
    await expect(page.getByText(PENDING_NOTICE)).toBeVisible();
    await capture(page, '33-admin-new-key-refused');

    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    /* 書いた内容がそのまま戻る（読み直しも初期化もしない） */
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
    await expect(page.getByLabel(ARTIST_LABEL)).toHaveValue('E2E 再認証アーティスト');
    await capture(page, '34-admin-new-key-accepted');

    await page.getByRole('button', { name: CREATE_LABEL }).click();

    await expect(page.getByRole('table')).toBeVisible();
    await expect(rowOf(page, title)).toContainText(DRAFT_LABEL);
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
