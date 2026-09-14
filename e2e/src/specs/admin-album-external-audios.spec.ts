import { setTimeout as delay } from 'node:timers/promises';

import type { Locator, Page } from '@playwright/test';

import { openAllSections } from '../support/album-editor.ts';
import { stack } from '../support/config.ts';
import { capture, captureFocused, captureWhole, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbum } from '../support/scratch-albums.ts';

/**
 * 管理画面の外部音源の編集（#122 / #155 / #391）のジャーニー。
 *
 * <p>
 * 追加・取り外し・並べ替えはどれも<b>入力の操作</b>で、作品の保存に乗って初めて反映される。作品の子を書く
 * 経路は集約ルートに1つしかないため、送るのは保存のときだけになる。ここで見るのは、押した時点では何も
 * 起きていないこと、そして保存が作品と音源を一緒に届けることである。
 * </p>
 *
 * <p>
 * 埋め込めるホストかどうかの判定はバックエンドが持つ。画面が弾いていないこと（実APIが断ったものを
 * そのまま出していること）まで見るため、応答は差し替えない（#164）。
 * </p>
 *
 * 文言は画面の実装が持つため、シナリオ側に置く。
 */

/** 鍵の入力欄と、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 一覧に置く導線 */
const EDIT_LABEL = '編集する';

/** 入力欄のラベル */
const TITLE_LABEL = 'タイトル';
const URL_LABEL = '音源のURL';
const COVER_CHOOSE_LABEL = '画像を選ぶ';

/** 外部音源のまとまりの操作 */
const ADD_LABEL = '音源を追加する';
const REMOVE_LABEL = '外す';
const UP_LABEL = '上へ';
const DOWN_LABEL = '下へ';
const AUDIOS_ABSENT_TEXT = '外部音源はありません。';

/** 保存の操作と、世代が古いことを伝える見出し */
const SAVE_LABEL = '保存する';
const CONFLICT_HEADING = '編集を始めた後に、別の操作がこの作品を保存しています';

/** 埋め込めるホストのURL。許可するホストの一覧はバックエンドが持つ */
const FIRST_URL = 'https://soundcloud.com/example/e2e-first';
const SECOND_URL = 'https://soundcloud.com/example/e2e-second';

/** 埋め込めないホストのURL。断られることを見るために使う */
const REJECTED_URL = 'https://example.com/e2e-not-embeddable';

/** 保存の経路。応答を遅らせて、保存中の状態を観測する余地を作る */
const SAVE_ALBUM_API = `${stack.backendBaseUrl}/api/v1/albums/*`;

/** 保存の応答を遅らせる時間 */
const SLOW_SAVE_MS = 2_000;

/** 鍵を入れて一覧が出た状態にする */
const openAdmin = async (page: Page): Promise<void> => {
  await page.goto(stack.adminBaseUrl);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

/**
 * 一覧から対象の編集を開き、区画をすべて開く。
 *
 * 区画は既定で畳まれている（#122）。ここで見るのは音源の行の振る舞いのため、先にまとめて開く。
 */
const openEdit = async (page: Page, title: string): Promise<void> => {
  await page
    .getByRole('row')
    .filter({ hasText: title })
    .getByRole('link', { name: EDIT_LABEL })
    .click();
  await openAllSections(page);
  await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
};

/** 外部音源の一覧と、その行 */
const audioList = (page: Page): Locator => page.locator('[data-external-audios]');
const audioRows = (page: Page): Locator => audioList(page).getByRole('listitem');

/** URLを入れて行を足す。送るのは保存のときで、ここでは入力が変わるだけ */
const addAudio = async (page: Page, url: string): Promise<void> => {
  await page.getByLabel(URL_LABEL).fill(url);
  await page.getByRole('button', { name: ADD_LABEL }).click();
};

const openAlbumFor = async (page: Page, purpose: string): Promise<string> => {
  const title = await seedScratchAlbum(purpose);

  await openAdmin(page);
  await openEdit(page, title);

  return title;
};

test.afterEach(deleteScratchAlbums);

test.describe('管理画面の外部音源', () => {
  test('URLを入れて足すと、保存する前から一覧に並ぶ', async ({ page }) => {
    await openAlbumFor(page, '音源追加');

    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);
    await expect(audioRows(page).first()).toContainText(FIRST_URL);

    await captureFocused(page, audioList(page), '39k-admin-external-audio-added');
  });

  test('外すと一覧から消える', async ({ page }) => {
    await openAlbumFor(page, '音源取り外し');

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);

    await page.getByRole('button', { name: REMOVE_LABEL }).click();
    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();
  });

  test('並べ替えると、行の位置と番号が入れ替わる', async ({ page }) => {
    await openAlbumFor(page, '音源並べ替え');

    await addAudio(page, FIRST_URL);
    await addAudio(page, SECOND_URL);
    await expect(audioRows(page)).toHaveCount(2);

    /* 足した行は末尾に付くため、入れる順がそのまま並びになる */
    await expect(audioRows(page).first()).toContainText(FIRST_URL);

    await audioRows(page).last().getByRole('button', { name: UP_LABEL }).click();
    await expect(audioRows(page).first()).toContainText(SECOND_URL);
    await expect(audioRows(page).last()).toContainText(FIRST_URL);

    /* 番号は配列の位置から描くため、入れ替えても 1..n のまま飛ばない */
    await expect(audioRows(page).first()).toContainText('1');
    await expect(audioRows(page).last()).toContainText('2');

    await captureFocused(page, audioList(page), '39m-admin-external-audio-reordered');
  });

  test('端では、それ以上動かせない', async ({ page }) => {
    await openAlbumFor(page, '音源の端');

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);

    await expect(audioRows(page).first().getByRole('button', { name: UP_LABEL })).toBeDisabled();
    await expect(audioRows(page).first().getByRole('button', { name: DOWN_LABEL })).toBeDisabled();
  });

  test('保存しないまま開き直すと、足した行は残っていない', async ({ page }) => {
    const title = await openAlbumFor(page, '音源未保存');

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);

    /*
     * UNSAVED-IS-NOT-SENT: 押した時点で送る経路を持たないため、保存しなければ作品は変わらない。
     * 読み直しで消えることが、入力に留まっていたことの証拠になる。
     */
    await page.reload();
    await openAllSections(page);
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();
  });

  test('保存すると、作品の項目と音源が一緒に残る', async ({ page }) => {
    const title = await openAlbumFor(page, '音源保存');
    const renamed = `${title} 保存済み`;

    await page.getByLabel(TITLE_LABEL).fill(renamed);
    await addAudio(page, FIRST_URL);
    await addAudio(page, SECOND_URL);

    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '39p-admin-external-audio-saving',
    );

    /* 保存が通れば一覧へ戻る */
    await expect(page.getByRole('table')).toBeVisible();

    await openEdit(page, renamed);
    await expect(audioRows(page)).toHaveCount(2);
    await expect(audioRows(page).first()).toContainText(FIRST_URL);
    await expect(audioRows(page).last()).toContainText(SECOND_URL);

    await capture(page, '39n-admin-external-audio-reopened');
  });

  test('埋め込めないホストは保存のときに断られ、その行に理由が出る', async ({ page }) => {
    await openAlbumFor(page, '音源却下');

    await addAudio(page, FIRST_URL);
    await addAudio(page, REJECTED_URL);

    await page.getByRole('button', { name: SAVE_LABEL }).click();

    /*
     * 断られた理由は実APIが返したものを、その行の下に出す。画面が先に弾いていれば、許可するホストの
     * 一覧を写していることになり、増減のたびに2箇所を直すことになる。
     */
    await expect(audioRows(page).last().getByRole('alert')).toBeVisible();
    await expect(audioRows(page).first().getByRole('alert')).toHaveCount(0);

    /* 断られたのだから、作品は保存されていない——画面も入力に留まる */
    await expect(page.getByLabel(TITLE_LABEL)).toBeVisible();
    await expect(audioRows(page)).toHaveCount(2);

    await captureFocused(page, audioList(page), '39l-admin-external-audio-rejected');
  });

  test('同じURLを2行入れて保存すると、重複の理由が出る（読み直しを促さない）', async ({ page }) => {
    await openAlbumFor(page, '音源重複');

    await addAudio(page, FIRST_URL);
    await addAudio(page, FIRST_URL);

    await page.getByRole('button', { name: SAVE_LABEL }).click();

    /*
     * SAME-STATUS-DIFFERENT-CAUSE: 子を集約ルート経由で書くようになり、同じ PUT が世代の競合と集約の
     * 業務違反の両方を 409 で返す（#391）。状態コードで見分けると、入力を直せば通る要求に「最新を
     * 読み込む」を促すことになる。実スタックで見るのは、画面が見ている型と backend が返す型が
     * 噛み合っていることまで含めるためである。
     */
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText(CONFLICT_HEADING)).toHaveCount(0);

    /* 断られたのだから、入力に留まる */
    await expect(page.getByLabel(TITLE_LABEL)).toBeVisible();
    await expect(audioRows(page)).toHaveCount(2);

    await captureFocused(page, page.getByRole('alert'), '39o-admin-external-audio-duplicated');
  });

  test('保存の最中は、作品の入力も音源の操作も受け付けない', async ({ page }) => {
    await openAlbumFor(page, '音源保存中');

    await addAudio(page, FIRST_URL);

    /* 応答を遅らせて、保存中の状態を観測できるようにする（要求そのものは通す） */
    await page.route(SAVE_ALBUM_API, async (route) => {
      await delay(SLOW_SAVE_MS);
      await route.continue();
    });

    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(page.getByLabel(TITLE_LABEL)).toBeDisabled();
    await expect(page.getByLabel(COVER_CHOOSE_LABEL)).toBeDisabled();
    await expect(
      audioRows(page).first().getByRole('button', { name: REMOVE_LABEL }),
    ).toBeDisabled();

    /* 塞がっていることは欄の全体で見る（#369） */
    await captureWhole(page, '39q-admin-external-audio-saving-blocked');

    /* 応答が返れば保存は成立し、一覧へ戻る */
    await expect(page.getByRole('table')).toBeVisible();

    await page.unroute(SAVE_ALBUM_API);
  });
});
