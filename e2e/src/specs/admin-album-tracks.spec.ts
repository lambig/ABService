import type { Locator, Page } from '@playwright/test';

import { capture, captureFocused, captureWhole } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbum } from '../support/scratch-albums.ts';
import { stack } from '../support/config.ts';

/**
 * 管理画面の曲目（トラックとチューン構成）の編集（#122 / #391）のジャーニー。
 *
 * <p>
 * 追加・取り外し・並べ替え・行の書き換えはどれも<b>入力の操作</b>で、作品の保存に乗って初めて反映される。
 * 作品の子を書く経路は集約ルートに1つしかないため、押した時点では何も送られない。ここで見るのは、
 * 保存するまで作品が変わらないこと、そして保存が作品と曲目を一緒に届けることである。
 * </p>
 *
 * <p>
 * 番号は入力の項目ではない。並びは配列の位置がそのまま表し、画面に出る番号もその位置から描く。
 * </p>
 *
 * <p>
 * トラックもチューンも一度に開くのは1行だけで、畳んだ行は出る形（公開サイトと同じ読み方）で並ぶ。
 * 全部を開くと縦に長すぎて、編集している場所を見失う。
 * </p>
 *
 * <p>
 * 検証は実APIが返したものを見る（#164）。行の位置つきのエラー（`tracks[0].tunes[0].linkUrl`）が、その行の
 * 欄の下に出ることまで確かめる——位置を捨てると、どの行が不正なのか読み手に分からない。
 * </p>
 *
 * 文言は画面の実装が持つため、シナリオ側に置く。
 */

/** 鍵の入力欄と、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 一覧に置く導線 */
const EDIT_LABEL = '編集する';

/** 作品の入力欄 */
const ALBUM_TITLE_LABEL = 'タイトル';
const ORIGINAL_WORK_NOTE_LABEL = '原作の出典（例:「○○」より各曲）';

/** 曲目のまとまりの操作 */
const ADD_TRACK_LABEL = 'トラックを追加する';
const REMOVE_TRACK_LABEL = '外す';
const UP_LABEL = '上へ';
const TRACKS_ABSENT_TEXT = '曲目はありません。';

/** トラックの入力欄 */
const TRACK_TITLE_LABEL = 'トラック名（省くとチューン名から組まれます）';

/** チューンのまとまりの操作 */
const ADD_TUNE_LABEL = 'チューンを足す';

/**
 * 行を指す文言。
 *
 * 何番目かを名に持つ（画面がそう出している）。行を指してから欄を引くのではなく名で指すのは、
 * **名だけでどの行かが読めること**を見るためである。
 */
const openTrackLabel = (order: number): string => `${String(order)}曲目を開く`;
const tuneTitleLabel = (order: number): string => `${String(order)}チューン目の曲名`;
const tuneComposerLabel = (order: number): string => `${String(order)}チューン目の作曲`;
const tuneLinkLabel = (order: number): string => `${String(order)}チューン目のリンクURL`;
const removeTuneLabel = (order: number): string => `${String(order)}チューン目を外す`;
const openTuneLabel = (order: number): string => `${String(order)}チューン目を開く`;

/** 保存の操作と、未保存を知らせる文言 */
const SAVE_LABEL = '保存する';
const UNSAVED_TEXT = '保存していない変更があります。';

/** 受け付けられないリンク。行の位置つきのエラーが返ることを見るために使う */
const REJECTED_LINK = 'not-a-url';

/** 鍵を入れて一覧が出た状態にする */
const openAdmin = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

/** 一覧から対象の編集を開く */
const openEdit = async (page: Page, title: string): Promise<void> => {
  await page
    .getByRole('row')
    .filter({ hasText: title })
    .getByRole('link', { name: EDIT_LABEL })
    .click();
  await expect(page.getByLabel(ALBUM_TITLE_LABEL)).toHaveValue(title);
};

const openAlbumFor = async (page: Page, purpose: string): Promise<string> => {
  const title = await seedScratchAlbum(purpose);

  await openAdmin(page);
  await openEdit(page, title);

  return title;
};

/** 曲目の一覧と、その行 */
const trackList = (page: Page): Locator => page.locator('[data-tracks]');

/** 直接の子だけを数える。畳んだ行の中にチューンの行が入れ子で並ぶため */
const trackRows = (page: Page): Locator => trackList(page).locator(':scope > li');

/** 畳んだ行に出ているチューンの並び */
const shownTunes = (row: Locator): Locator => row.locator('[data-track-tunes] > li');

/** 開いている行の入力と、その中のチューンの行 */
const editor = (page: Page): Locator => page.locator('[data-track-editor]');
const tuneRows = (page: Page): Locator => editor(page).locator('[data-tune]');

/** 畳んだチューンの行に出ている文言 */
const tuneSummaries = (page: Page): Locator => editor(page).locator('[data-tune-summary]');

/** トラックを1つ足して、その行の名を入れる。足した行はそのまま開く（画面がそう組んでいる） */
const addTrack = async (page: Page, title: string): Promise<void> => {
  await page.getByRole('button', { name: ADD_TRACK_LABEL }).click();
  await page.getByLabel(TRACK_TITLE_LABEL).fill(title);
};

/** チューンの行を1つ足して、その行の曲名を入れる。足した行はそのまま開く */
const addTune = async (page: Page, index: number, title: string): Promise<void> => {
  await page.getByRole('button', { name: ADD_TUNE_LABEL }).click();
  await page.getByLabel(tuneTitleLabel(index + 1)).fill(title);
};

test.afterEach(deleteScratchAlbums);

test.describe('管理画面の曲目', () => {
  test('トラックを足すと、保存する前から曲目に並ぶ', async ({ page }) => {
    await openAlbumFor(page, '曲目追加');

    await expect(page.getByText(TRACKS_ABSENT_TEXT)).toBeVisible();

    await addTrack(page, 'E2E 1曲目');

    await expect(trackRows(page)).toHaveCount(1);
    await expect(trackRows(page).first()).toContainText('E2E 1曲目');

    /* 足しただけで画面の外に書きかけが生まれる。下端の知らせがそれを伝える */
    await expect(page.getByText(UNSAVED_TEXT)).toBeVisible();

    await captureFocused(page, trackList(page), '39r-admin-track-added');
  });

  test('トラック名を省いた行は、名が組まれることだけを示す', async ({ page }) => {
    await openAlbumFor(page, '曲目名なし');

    await addTrack(page, '');
    await addTune(page, 0, 'E2E 名なしのチューン');

    /*
     * 名の組み方（区切り）はバックエンドの設定が持つ（#360）。画面が繋ぐと、区切りを変えたときに
     * 2箇所を直すことになるため、材料であるチューンの行を並べるに留める。
     */
    await expect(trackRows(page).first()).toContainText('（チューン名から組まれます）');
    await expect(shownTunes(trackRows(page).first()).first()).toContainText('E2E 名なしのチューン');
  });

  test('チューン構成つきで保存し、開き直すと同じ行が入っている', async ({ page }) => {
    const title = await openAlbumFor(page, '曲目構成');
    const renamed = `${title} 保存済み`;

    await page.getByLabel(ALBUM_TITLE_LABEL).fill(renamed);
    await addTrack(page, 'E2E 組曲');
    await addTune(page, 0, 'E2E 前半');
    await addTune(page, 1, 'E2E 後半');

    /* 2行目を足した時点で1行目は畳まれている。触るには開き直す */
    await page.getByRole('button', { name: openTuneLabel(1) }).click();
    await page.getByLabel(tuneComposerLabel(1)).fill('E2E 作曲者');

    /* 開いた行の入力の姿。畳んだ分だけ縦幅が縮む（#369 のため全体で撮る） */
    await captureWhole(page, '39s-admin-track-editor');

    await page.getByRole('button', { name: SAVE_LABEL }).click();

    /* 保存が通れば一覧へ戻る。作品と曲目は同じ1回の保存で届く */
    await expect(page.getByRole('table')).toBeVisible();

    await openEdit(page, renamed);
    await expect(trackRows(page)).toHaveCount(1);

    /*
     * 畳んだ形は公開サイトと同じ読み方で出す。ここで構成が見えないと、開くまで何が入っているのかが
     * 分からない。
     */
    await expect(shownTunes(trackRows(page).first())).toHaveCount(2);
    await expect(shownTunes(trackRows(page).first()).first()).toContainText(
      'E2E 前半（作曲: E2E 作曲者）',
    );
    await expect(shownTunes(trackRows(page).first()).last()).toContainText('E2E 後半');

    await capture(page, '39n-admin-track-reopened');

    await page.getByRole('button', { name: openTrackLabel(1) }).click();
    await expect(tuneRows(page)).toHaveCount(2);

    /* 開き直すまでは、チューンのどの行も畳んだ形で並ぶ */
    await expect(page.getByLabel(tuneTitleLabel(1))).toHaveCount(0);
    await expect(tuneSummaries(page).first()).toContainText('E2E 前半（作曲: E2E 作曲者）');

    await page.getByRole('button', { name: openTuneLabel(1) }).click();
    await expect(page.getByLabel(tuneTitleLabel(1))).toHaveValue('E2E 前半');
    await expect(page.getByLabel(tuneComposerLabel(1))).toHaveValue('E2E 作曲者');

    await captureWhole(page, '39w-admin-track-tune-open');

    await page.getByRole('button', { name: openTuneLabel(2) }).click();
    await expect(page.getByLabel(tuneTitleLabel(2))).toHaveValue('E2E 後半');

    /* 開くのは1行だけ。前の行は畳まれる */
    await expect(page.getByLabel(tuneTitleLabel(1))).toHaveCount(0);

    await captureWhole(page, '39x-admin-track-tune-switched');
  });

  test('畳んでも書きかけは消えない', async ({ page }) => {
    await openAlbumFor(page, '曲目畳み');

    await addTrack(page, 'E2E 畳む曲');
    await addTune(page, 0, 'E2E 書きかけ');

    /*
     * KEEP-WHILE-COLLAPSED: 入力は作品の下書きが持ち、行が持つのは見え方だけである（#391）。畳んで
     * 開き直したときに消えていれば、行が入力を抱えていることになる。
     */
    await page.getByRole('button', { name: '1曲目を畳む' }).click();
    await expect(editor(page)).toHaveCount(0);

    await page.getByRole('button', { name: openTrackLabel(1) }).click();
    await expect(page.getByLabel(TRACK_TITLE_LABEL)).toHaveValue('E2E 畳む曲');
    await expect(tuneSummaries(page).first()).toContainText('E2E 書きかけ');
  });

  test('チューンの行を外して保存すると、その行は消える（更新は全項目置換）', async ({ page }) => {
    const title = await openAlbumFor(page, '曲目行外し');
    const renamed = `${title} 保存済み`;

    await page.getByLabel(ALBUM_TITLE_LABEL).fill(renamed);
    await addTrack(page, 'E2E 2行');
    await addTune(page, 0, 'E2E 残す');
    await addTune(page, 1, 'E2E 消す');

    await page.getByRole('button', { name: removeTuneLabel(2) }).click();
    await expect(tuneRows(page)).toHaveCount(1);

    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    await openEdit(page, renamed);
    await expect(shownTunes(trackRows(page).first())).toHaveCount(1);
    await expect(shownTunes(trackRows(page).first()).first()).toContainText('E2E 残す');
  });

  test('行の誤りは、その行の欄の下に出る', async ({ page }) => {
    await openAlbumFor(page, '曲目行の誤り');

    await addTrack(page, 'E2E 誤り');
    await addTune(page, 0, 'E2E 1行目');
    await page.getByLabel(tuneLinkLabel(1)).fill(REJECTED_LINK);

    /* 2行目を足すと1行目は畳まれる。誤りは、畳まれた行の中にある状態で送る */
    await addTune(page, 1, 'E2E 2行目');
    await expect(page.getByLabel(tuneLinkLabel(1))).toHaveCount(0);

    await page.getByRole('button', { name: SAVE_LABEL }).click();

    /*
     * 位置は実APIが `tracks[0].tunes[0].linkUrl` として返す。**1行目の下に出ていること**まで見ないと、
     * どの行が不正なのかを画面が伝えられているとは言えない。畳まれたままでは理由が読めないため、
     * 断られた行は開く。
     */
    await expect(
      tuneRows(page).first().locator('[data-field="tracks[0].tunes[0].linkUrl"]'),
    ).toContainText(/.+/u);
    await expect(tuneRows(page).last().getByRole('alert')).toHaveCount(0);

    /* 断られたのだから、画面は入力に留まる */
    await expect(page.getByLabel(ALBUM_TITLE_LABEL)).toBeVisible();

    await captureWhole(page, '39t-admin-track-tune-error');
  });

  test('外すと曲目から消える', async ({ page }) => {
    await openAlbumFor(page, '曲目取り外し');

    await addTrack(page, 'E2E 消す曲');
    await expect(trackRows(page)).toHaveCount(1);

    /* 「Nチューン目を外す」とは別の操作である。緩く指すと、開いている行の中の操作に当たる */
    await trackRows(page)
      .first()
      .getByRole('button', { name: REMOVE_TRACK_LABEL, exact: true })
      .click();
    await expect(page.getByText(TRACKS_ABSENT_TEXT)).toBeVisible();
  });

  test('並べ替えると、行の位置と番号が入れ替わる', async ({ page }) => {
    await openAlbumFor(page, '曲目並べ替え');

    await addTrack(page, 'E2E 1番目');
    await addTrack(page, 'E2E 2番目');
    await expect(trackRows(page)).toHaveCount(2);

    /* 足した行は末尾に付くため、入れる順がそのまま並びになる */
    await expect(trackRows(page).first()).toContainText('E2E 1番目');

    await trackRows(page).last().getByRole('button', { name: UP_LABEL }).click();
    await expect(trackRows(page).first()).toContainText('E2E 2番目');

    /* 番号は配列の位置から描くため、入れ替えても 1..n のまま飛ばない */
    await expect(trackRows(page).first()).toContainText('1');
    await expect(trackRows(page).last()).toContainText('2');

    await captureFocused(page, trackList(page), '39u-admin-track-reordered');
  });

  test('保存しないまま開き直すと、足した行は残っていない', async ({ page }) => {
    const title = await openAlbumFor(page, '曲目未保存');

    await addTrack(page, 'E2E 送らない曲');
    await expect(trackRows(page)).toHaveCount(1);

    /*
     * UNSAVED-IS-NOT-SENT: 押した時点で送る経路を持たないため、保存しなければ作品は変わらない。
     * 読み直しで消えることが、入力に留まっていたことの証拠になる。
     */
    await page.reload();
    await expect(page.getByLabel(ALBUM_TITLE_LABEL)).toHaveValue(title);
    await expect(page.getByText(TRACKS_ABSENT_TEXT)).toBeVisible();
    await expect(page.getByText(UNSAVED_TEXT)).toHaveCount(0);
  });

  test('原作の出典は曲目の直後にあり、同じ保存で届く', async ({ page }) => {
    const title = await openAlbumFor(page, '曲目と出典');
    const renamed = `${title} 保存済み`;

    await page.getByLabel(ALBUM_TITLE_LABEL).fill(renamed);
    await addTrack(page, 'E2E 出典つきの曲');

    /*
     * 「「○○」より各曲」は曲目そのものを指す一文である（#365）。フォームの囲みの外にあるが、`form`
     * 属性で同じ保存へ乗る——1回の保存で届くことまで見ないと、外に出したことが効いているか分からない。
     */
    await page.getByLabel(ORIGINAL_WORK_NOTE_LABEL).fill('「E2E原作」より各曲');

    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    await openEdit(page, renamed);
    await expect(page.getByLabel(ORIGINAL_WORK_NOTE_LABEL)).toHaveValue('「E2E原作」より各曲');
    await expect(trackRows(page)).toHaveCount(1);

    await captureWhole(page, '39v-admin-track-original-work-note');
  });
});
