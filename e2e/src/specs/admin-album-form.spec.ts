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
  deleteScratchAlbums,
  scratchCatalogPrefix,
  seedScratchAlbum,
  seedScratchAlbumDetail,
} from '../support/scratch-albums.ts';

/**
 * 通常の作成・保存・再読込と、画像確定・競合・再認証の実接続を守る。
 * 保存中・通信失敗・入力エラーの表示は AlbumForm.test.ts、入力変換は album-form.test.ts が担当。
 * 配置は実ブラウザでしか測れないため、修正して保存する代表経路で確認する（#575）。
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

  expect(leftBox).not.toBeNull();
  expect(rightBox).not.toBeNull();
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
      new RegExp(`^${scratchCatalogPrefix()}`, 'u'),
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

  test('直して保存すると、一覧に反映される', async ({ page }) => {
    const title = await seedScratchAlbum('保存');
    const renamed = `${title} 改題`;

    await openAdmin(page);
    await openEdit(page, title);

    await page.getByLabel(TITLE_LABEL).fill('   ');
    await page.getByLabel(BASE_PRICE_LABEL).fill('-1');
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(fieldOf(page, 'title').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'basePrice.amount').getByRole('alert')).toBeVisible();

    /* 通常の入力行と解除ボタンを持つ行の配置を、実400応答の経路に集約する。 */
    await expectAlignedRow(page, CATALOG_NUMBER_LABEL, TITLE_LABEL);
    await expectAlignedRow(page, BASE_PRICE_LABEL, CURRENCY_LABEL);
    const amountBox = await page.getByLabel(BASE_PRICE_LABEL).boundingBox();
    const clearBox = await page.getByRole('button', { name: CLEAR_BASE_PRICE_LABEL }).boundingBox();
    expect(amountBox).not.toBeNull();
    expect(clearBox).not.toBeNull();
    expect(amountBox?.y).toBe(clearBox?.y);

    /* 同じ画面のまま直して送り直せること（入力をやり直させない） */
    await page.getByLabel(TITLE_LABEL).fill(renamed);
    await page.getByLabel(BASE_PRICE_LABEL).fill(String(SCRATCH_BASE_PRICE));
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '26-admin-edit-save',
    );

    await expect(page.getByRole('table')).toBeVisible();
    await expect(rowOf(page, renamed)).toBeVisible();
    await captureFocused(page, rowOf(page, renamed), '27-admin-edit-saved');
    await openEdit(page, renamed);
    await expect(page.getByLabel(BASE_PRICE_LABEL)).toHaveValue(String(SCRATCH_BASE_PRICE));
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
    await page.getByLabel(CATALOG_NUMBER_LABEL).fill(`${scratchCatalogPrefix()}${stamp}`);

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
    await page.getByLabel(CATALOG_NUMBER_LABEL).fill(`${scratchCatalogPrefix()}${stamp}`);
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

  test('名義を省略して作成・更新すると共通名義が保存される', async ({ page }) => {
    const stamp = String(Date.now());
    const title = `E2E 共通名義の作品 ${stamp}`;
    await openAdmin(page);
    await page.goto(`${stack.adminBaseUrl}/site-contents`);
    await page
      .getByRole('row')
      .filter({ hasText: 'site.artist' })
      .getByRole('button', { name: EDIT_LABEL })
      .click();
    const commonArtist = await page.getByLabel('本文').inputValue();
    expect(commonArtist.trim()).not.toBe('');

    await page.goto(`${stack.adminBaseUrl}/albums/new`);
    await openSection(page, '作品');
    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByLabel(RELEASE_DATE_LABEL).fill('2026-10-01');
    await page.getByLabel(CATALOG_NUMBER_LABEL).fill(`${scratchCatalogPrefix()}${stamp}`);
    await expect(page.getByLabel(ARTIST_LABEL)).toHaveValue('');
    await page.getByRole('button', { name: CREATE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();
    await openEdit(page, title);
    await expect(page.getByLabel(ARTIST_LABEL)).toHaveValue(commonArtist.trim());
    await page.getByLabel(ARTIST_LABEL).fill('   ');
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();
    await openEdit(page, title);
    await expect(page.getByLabel(ARTIST_LABEL)).toHaveValue(commonArtist.trim());
  });
});
