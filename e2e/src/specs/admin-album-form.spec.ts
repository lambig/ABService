import { setTimeout as delay } from 'node:timers/promises';

import type { Locator, Page } from '@playwright/test';
import { revokeBrowserSession } from '../support/admin-sessions.ts';

import { renameAlbumOutsideTheScreen } from '../support/admin-api.ts';
import { openAllSections, openSection } from '../support/album-editor.ts';
import { stack } from '../support/config.ts';
import {
  acceptedCoverImage,
  unconfirmableCoverImage,
  unsupportedCoverImage,
} from '../support/cover-image.ts';
import { capture, captureFocused, captureWhole, clickWithEvidence } from '../support/evidence.ts';
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
const EVENT_DATE_LABEL = '開催日';
const EVENT_NAME_LABEL = 'イベント名';
const EVENT_PLACE_LABEL = '会場';
const BASE_PRICE_LABEL = '基準額';
const CURRENCY_LABEL = '通貨コード（未指定は円）';
const ORIGINAL_WORK_NOTE_LABEL = '原作の出典（例:「○○」より各曲）';

/** 頒布のまとまりを外す操作 */
const CLEAR_BASE_PRICE_LABEL = '基準額を解除';

/** カバー画像のまとまり */
const COVER_CHOOSE_LABEL = '画像を選ぶ';
const CLEAR_COVER_LABEL = 'カバー画像を外す';
const COVER_ABSENT_TEXT = 'カバー画像はありません。';

/** 保存の操作 */
const SAVE_LABEL = '保存する';
const CREATE_LABEL = '作成する';

/** 未公開であることを示すラベル */
const DRAFT_LABEL = '下書き';

/** 更新の経路。到達できない状態・応答が遅い状態を作るために塞ぐ */
const UPDATE_API = `${stack.backendBaseUrl}/api/v1/albums/*`;

/** 保存の応答を遅らせる時間。保存中の状態を観測する余地を作る */
const SLOW_SAVE_MS = 2_000;

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

/** カバー画像のまとまりと、その中に出ている画像 */
const coverSection = (page: Page): Locator => fieldOf(page, 'coverImageKey');
const coverImage = (page: Page): Locator => coverSection(page).locator('[data-cover-image]');

/**
 * カバー画像が本当に届いて描かれていること。
 *
 * <p>
 * `src` が入っただけの状態と区別する。配信が `/assets/*` を取り次いでいない場合も、実体が確定して
 * いない場合も、画面には要素がある。読み込めたかどうかは実寸でしか分からない。
 * </p>
 */
const expectCoverDrawn = async (page: Page): Promise<void> => {
  await expect(coverImage(page)).toHaveJSProperty('naturalWidth', acceptedCoverImage.width);
};

/** 画像を選び、確定して画面に出るまで待つ */
const chooseCover = async (page: Page): Promise<void> => {
  await page.getByLabel(COVER_CHOOSE_LABEL).setInputFiles(acceptedCoverImage);
  await expect(coverImage(page)).toBeVisible();
  await expectCoverDrawn(page);
};

/**
 * いま出ているカバー画像の配信先。
 *
 * 差し替わっていないことを見るために控える。鍵そのものは画面に出ないが、配信先は確定の応答が返した
 * 鍵から組まれるため、これが変わらないことは鍵が変わっていないことを表す。
 */
const coverSourceOf = async (page: Page): Promise<string> => {
  const source = await coverImage(page).getAttribute('src');

  return source === null ? Promise.reject(new Error('カバー画像が src を持っていません')) : source;
};

/**
 * 同じ行に並ぶ2つの欄が、上端で揃っていること。
 *
 * <p>
 * 誤りは欄の下に出るため、欄ごとに高さが変わる。下端で揃えていると、誤りの出た欄だけが持ち上がって
 * 行の並びが崩れる——**落ちない欠陥**なので、位置そのものを見る。
 * </p>
 */
const expectAlignedRow = async (page: Page, left: string, right: string): Promise<void> => {
  const leftBox = await page.getByLabel(left).boundingBox();
  const rightBox = await page.getByLabel(right).boundingBox();

  expect(leftBox?.y).toBe(rightBox?.y);
};

/**
 * 一覧から対象の編集を開き、区画をすべて開く。
 *
 * 区画は既定で畳まれている（#122）。ここで見るのは欄そのものの振る舞いのため、畳み方は
 * `admin-album-sections.spec.ts` へ任せ、先にまとめて開く。
 */
const openEdit = async (page: Page, title: string): Promise<void> => {
  await rowOf(page, title).getByRole('link', { name: EDIT_LABEL }).click();
  await openAllSections(page);
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

    await openAllSections(page);

    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
    await expect(page.getByLabel(RELEASE_DATE_LABEL)).toHaveValue('2026-09-01');
    await expect(page.getByLabel(CATALOG_NUMBER_LABEL)).toHaveValue(
      new RegExp(`^${SCRATCH_CATALOG_PREFIX}`, 'u'),
    );
    await expect(page.getByLabel(BASE_PRICE_LABEL)).toHaveValue(String(SCRATCH_BASE_PRICE));

    /* 確かめているのはタイトルから基準額までで、1画面に収まらない（#369） */
    await captureWhole(page, '23-admin-edit-loaded');
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
    await captureFocused(page, page.getByLabel(BASE_PRICE_LABEL), '39-admin-edit-base-price-saved');
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
    await captureFocused(
      page,
      page.getByLabel(BASE_PRICE_LABEL),
      '39b-admin-edit-base-price-cleared',
    );
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
    await captureFocused(
      page,
      page.getByLabel(ORIGINAL_WORK_NOTE_LABEL),
      '39d-admin-edit-original-work-note-saved',
    );
  });

  test('カバー画像を選ぶと確定され、保存すると読み直した編集に残っている', async ({ page }) => {
    const title = await seedScratchAlbum('カバー画像');

    await openAdmin(page);
    await openEdit(page, title);

    /* 作品は画像を持たない状態で作られる。無い側から入れて、往復することを見る */
    await expect(coverSection(page).getByText(COVER_ABSENT_TEXT)).toBeVisible();

    await chooseCover(page);
    await captureFocused(page, coverSection(page), '39e-admin-edit-cover-chosen');

    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    /*
     * 確定は選んだ時点で済んでおり、作品へ結び付くのはこの保存。読み直して初めて、**送れたこと**と
     * **保存されたこと**の両方が揃ったと言える。
     */
    await openEdit(page, title);
    await expect(coverImage(page)).toBeVisible();
    await expectCoverDrawn(page);
    await captureFocused(page, coverSection(page), '39f-admin-edit-cover-saved');
  });

  test('対応していない形式を選ぶと断られ、カバー画像は変わらない', async ({ page }) => {
    const title = await seedScratchAlbum('カバー画像の拒否');

    await openAdmin(page);
    await openEdit(page, title);

    await page.getByLabel(COVER_CHOOSE_LABEL).setInputFiles(unsupportedCoverImage);

    /* 断られた理由はバックエンドの文言をそのまま出す。画面は受け入れる形式の一覧を持たない */
    await expect(coverSection(page).getByRole('alert')).toBeVisible();
    await expect(coverSection(page).getByText(COVER_ABSENT_TEXT)).toBeVisible();
    await captureFocused(page, coverSection(page), '39g-admin-edit-cover-rejected');

    /* 断られた後も選び直せる。直す先は画像の選び直しにあり、入力を作り直してそこへ戻す */
    await chooseCover(page);
    await expect(coverSection(page).getByRole('alert')).toHaveCount(0);
  });

  test('送れても確定に通らない実体は、いま出ているカバー画像を置き換えない', async ({ page }) => {
    const title = await seedScratchAlbum('カバー画像の確定拒否');

    await openAdmin(page);
    await openEdit(page, title);
    await chooseCover(page);

    const confirmed = await coverSourceOf(page);

    /*
     * 申告は PNG で中身が PNG でない実体。払い出しも保管先への送信も通り、確定の検査で初めて落ちる。
     * 3段のうち最後だけが拒む唯一の経路で、「送れた実体でも確定に通らなければ鍵にしない」はここでしか
     * 踏めない（形式そのものが弾かれる場合は、送信も確定も起きない）。
     */
    await page.getByLabel(COVER_CHOOSE_LABEL).setInputFiles(unconfirmableCoverImage);

    await expect(coverSection(page).getByRole('alert')).toBeVisible();
    await expect(coverImage(page)).toHaveAttribute('src', confirmed);
    await expectCoverDrawn(page);
    await captureFocused(page, coverSection(page), '39j-admin-edit-cover-unconfirmable');

    /*
     * 保存して読み直す。断られた実体が入力の鍵に触れていないことは、画面に出ている配信先だけでは
     * 言い切れない——**保存されたのがどちらの鍵か**は、保存を通してからでないと分からない。
     */
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    await openEdit(page, title);
    await expect(coverImage(page)).toHaveAttribute('src', confirmed);
    await expectCoverDrawn(page);
  });

  test('カバー画像を外して保存すると、読み直した編集で持たない', async ({ page }) => {
    const title = await seedScratchAlbum('カバー画像の解除');

    await openAdmin(page);
    await openEdit(page, title);
    await chooseCover(page);
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    await openEdit(page, title);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: CLEAR_COVER_LABEL }),
      '39h-admin-edit-cover-clear',
    );

    await expect(coverImage(page)).toHaveCount(0);
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    await openEdit(page, title);
    await expect(coverSection(page).getByText(COVER_ABSENT_TEXT)).toBeVisible();
    await captureFocused(page, coverSection(page), '39i-admin-edit-cover-cleared');
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

    /* 誤りが出ても、額と通貨と解除の操作は同じ行の上端で揃ったまま */
    await expectAlignedRow(page, BASE_PRICE_LABEL, CURRENCY_LABEL);

    const amountBox = await page.getByLabel(BASE_PRICE_LABEL).boundingBox();
    const clearBox = await page.getByRole('button', { name: CLEAR_BASE_PRICE_LABEL }).boundingBox();

    expect(amountBox?.y).toBe(clearBox?.y);
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

    /*
     * ROW-ALIGNS-AT-TOP: 理由は欄の下に出るため、誤りのある欄だけが高くなる。同じ行の欄が上端で
     * 揃っていないと、誤りが1つ出るたびに並びが崩れて読めなくなる。
     */
    await expectAlignedRow(page, CATALOG_NUMBER_LABEL, TITLE_LABEL);
    await expectAlignedRow(page, RELEASE_DATE_LABEL, ISDN_LABEL);
    await expectAlignedRow(page, EVENT_DATE_LABEL, EVENT_NAME_LABEL);

    /* 見どころは「どの欄に出て、どの欄に出ていないか」。欄をまたぐため丸ごと撮る（#369） */
    await captureWhole(page, '25-admin-edit-field-errors');
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
    await captureFocused(page, rowOf(page, renamed), '27-admin-edit-saved');
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

    /* 理由の提示と、保たれた入力の両方が見どころ（#369） */
    await captureWhole(page, '28-admin-edit-unreachable');

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

    /* 塞がっていることは欄の全体で見る（#369） */
    await captureWhole(page, '32-admin-edit-saving');

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

    /* 競合の伝え方と、保たれた入力の両方が見どころ（#369） */
    await captureWhole(page, '36-admin-edit-conflicted');

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

    await openSection(page, '作品');
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
    await captureFocused(page, rowOf(page, title), '31-admin-new-created');
  });

  test('鍵が断られても入力は残り、入れ直せば続けて作成できる', async ({ page }) => {
    const stamp = String(Date.now());
    const title = `E2E 鍵を入れ直して追加した作品 ${stamp}`;

    /* 認証後にサーバーで失効しても、保存前に書いた入力を再認証へ引き継ぐ。 */
    await page.goto(`${stack.adminBaseUrl}/albums/new`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await openSection(page, '作品');
    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByLabel(RELEASE_DATE_LABEL).fill('2026-11-01');
    await page.getByLabel(ARTIST_LABEL).fill('E2E 再認証アーティスト');
    await page.getByLabel(CATALOG_NUMBER_LABEL).fill(`${SCRATCH_CATALOG_PREFIX}${stamp}`);
    await revokeBrowserSession(page);

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

    /* 書いた内容が欄をまたいで戻っていることが見どころ（#369） */
    await captureWhole(page, '34-admin-new-key-accepted');

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
