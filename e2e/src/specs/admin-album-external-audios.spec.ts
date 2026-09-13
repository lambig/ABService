import { setTimeout as delay } from 'node:timers/promises';

import type { Locator, Page } from '@playwright/test';

import { stack } from '../support/config.ts';
import { capture, captureFocused, captureWhole, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { deleteScratchAlbums, seedScratchAlbum } from '../support/scratch-albums.ts';

/**
 * 管理画面の外部音源の編集（#122 / #155）のジャーニー。
 *
 * <p>
 * 追加・取り外し・並べ替えは作品の保存とは別の経路で、押した時点で反映される。どれも Album 集約を
 * 保存するため**作品の世代が進む**。したがって未保存の入力を抱えたまま操作すると、その後の保存が
 * 競合として断られる。ここで見るのは、その噛み合わせが画面の上で成立していることである。
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
const ADD_LABEL = '追加する';
const REMOVE_LABEL = '外す';
const UP_LABEL = '上へ';
const DOWN_LABEL = '下へ';
const AUDIOS_ABSENT_TEXT = '外部音源はありません。';

/** 先に保存してよいかを尋ねる文言と、その返事 */
const CONFIRM_TEXT = '保存していない入力があります';
const CONFIRM_LABEL = '保存して続ける';
const CANCEL_LABEL = 'やめる';

/** 中止したことを伝える文言 */
const ABORTED_TEXT = '作品を保存していないため、追加は行いませんでした。';

/** 保存の操作と、競合したことを伝える見出し */
const SAVE_LABEL = '保存する';
const CONFLICT_HEADING = '編集を始めた後に、別の操作がこの作品を保存しています';

/** 埋め込めるホストのURL。許可するホストの一覧はバックエンドが持つ */
const FIRST_URL = 'https://soundcloud.com/example/e2e-first';
const SECOND_URL = 'https://soundcloud.com/example/e2e-second';

/** 埋め込めないホストのURL。断られることを見るために使う */
const REJECTED_URL = 'https://example.com/e2e-not-embeddable';

/** 音源を足す経路。応答を遅らせて、操作中の状態を観測する余地を作る */
const ADD_AUDIO_API = `${stack.backendBaseUrl}/api/v1/albums/*/external-audios`;

/** 音源の応答を遅らせる時間 */
const SLOW_AUDIO_MS = 2_000;

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
  await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
};

/** 外部音源の一覧と、その行 */
const audioList = (page: Page): Locator => page.locator('[data-external-audios]');
const audioRows = (page: Page): Locator => audioList(page).getByRole('listitem');

/** URLを入れて追加する。反映されるまで待つ */
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
  test('URLを入れて追加すると、一覧に並ぶ', async ({ page }) => {
    await openAlbumFor(page, '音源追加');

    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);
    await expect(audioRows(page).first()).toContainText(FIRST_URL);

    await captureFocused(page, audioList(page), '39k-admin-external-audio-added');
  });

  test('埋め込めないホストは断られ、音源は増えない', async ({ page }) => {
    await openAlbumFor(page, '音源却下');

    await addAudio(page, REJECTED_URL);

    /*
     * 断られた理由は実APIが返したものを出す。画面が先に弾いていれば、許可するホストの一覧を写して
     * いることになり、増減のたびに2箇所を直すことになる。
     */
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();

    await capture(page, '39l-admin-external-audio-rejected');
  });

  test('外すと一覧から消える', async ({ page }) => {
    await openAlbumFor(page, '音源取り外し');

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);

    await page.getByRole('button', { name: REMOVE_LABEL }).click();
    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();
  });

  test('並べ替えると、表示順が入れ替わる', async ({ page }) => {
    await openAlbumFor(page, '音源並べ替え');

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);
    await addAudio(page, SECOND_URL);
    await expect(audioRows(page)).toHaveCount(2);

    /* 追加は末尾に採番されるため、入れる順がそのまま表示順になる */
    await expect(audioRows(page).first()).toContainText(FIRST_URL);

    await audioRows(page).last().getByRole('button', { name: UP_LABEL }).click();
    await expect(audioRows(page).first()).toContainText(SECOND_URL);
    await expect(audioRows(page).last()).toContainText(FIRST_URL);

    /* 表示順は常に 1..n の連番で、入れ替えても飛ばない */
    await expect(audioRows(page).first()).toContainText('1');
    await expect(audioRows(page).last()).toContainText('2');

    await captureFocused(page, audioList(page), '39m-admin-external-audio-reordered');
  });

  test('操作の最中は、作品の入力を受け付けない', async ({ page }) => {
    await openAlbumFor(page, '音源操作中');

    /* 応答を遅らせて、操作中の状態を観測できるようにする（要求そのものは通す） */
    await page.route(ADD_AUDIO_API, async (route) => {
      await delay(SLOW_AUDIO_MS);
      await route.continue();
    });

    await addAudio(page, FIRST_URL);

    /*
     * 操作が済むと読み直すため、その間に書いた入力は保存されないまま消える。塞いでいなければ、
     * 消えたことにも気付けない。
     */
    await expect(page.getByLabel(TITLE_LABEL)).toBeDisabled();
    await expect(page.getByLabel(COVER_CHOOSE_LABEL)).toBeDisabled();

    /* 塞がっていることは欄の全体で見る（#369） */
    await captureWhole(page, '39q-admin-external-audio-running');

    /* 応答が返れば操作は成立し、読み直して入力へ戻る */
    await expect(audioRows(page)).toHaveCount(1);
    await expect(page.getByLabel(TITLE_LABEL)).toBeEnabled();

    await page.unroute(ADD_AUDIO_API);
  });

  test('端では、それ以上動かせない', async ({ page }) => {
    await openAlbumFor(page, '音源の端');

    await addAudio(page, FIRST_URL);
    await expect(audioRows(page)).toHaveCount(1);

    await expect(audioRows(page).first().getByRole('button', { name: UP_LABEL })).toBeDisabled();
    await expect(audioRows(page).first().getByRole('button', { name: DOWN_LABEL })).toBeDisabled();
  });
});

test.describe('未保存の入力があるときの外部音源', () => {
  test('先に保存してよいかを尋ね、断ると何も起きない', async ({ page }) => {
    const title = await openAlbumFor(page, '音源中止');

    await page.getByLabel(TITLE_LABEL).fill(`${title} 書きかけ`);
    await addAudio(page, FIRST_URL);

    await expect(page.getByText(CONFIRM_TEXT)).toBeVisible();
    await captureFocused(page, page.getByText(CONFIRM_TEXT), '39n-admin-external-audio-confirm');

    await clickWithEvidence(
      page,
      page.getByRole('button', { name: CANCEL_LABEL }),
      '39o-admin-external-audio-cancel',
    );

    /*
     * 断られたのだから、保存も音源の操作も起きていない。押した操作が実行されなかったことまで画面が
     * 言う——保存の失敗だけが見えると、追加されたのかどうかが読めない。
     */
    await expect(page.getByText(ABORTED_TEXT)).toBeVisible();
    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(`${title} 書きかけ`);
  });

  test('保存して続けると、入力も音源も残り、そのまま保存し直せる', async ({ page }) => {
    const title = await openAlbumFor(page, '音源保存');
    const renamed = `${title} 保存済み`;

    await page.getByLabel(TITLE_LABEL).fill(renamed);
    await addAudio(page, FIRST_URL);

    await page.getByRole('button', { name: CONFIRM_LABEL }).click();

    /* 書きかけだった入力は保存され、音源も付いている */
    await expect(audioRows(page)).toHaveCount(1);
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(renamed);

    /*
     * ここが噛み合わせの要である。音源の操作でも世代は進むため、読み直して取り直していなければ、
     * 続けての保存は競合として断られる。
     */
    /* 保存が通れば一覧へ戻る。競合していないことは、戻れたことで分かる */
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();
    await expect(page.getByText(CONFLICT_HEADING)).toHaveCount(0);

    await capture(page, '39p-admin-external-audio-saved');
  });

  test('保存した後に音源が断られても、続けて保存できる', async ({ page }) => {
    const title = await openAlbumFor(page, '音源却下後');
    const renamed = `${title} 保存済み`;

    await page.getByLabel(TITLE_LABEL).fill(renamed);
    await addAudio(page, REJECTED_URL);
    await page.getByRole('button', { name: CONFIRM_LABEL }).click();

    /* 保存は通り、音源だけが断られた */
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText(AUDIOS_ABSENT_TEXT)).toBeVisible();

    /*
     * 音源が変わっていないため読み直しは起きない。それでも保存で世代は進んでいるので、返った世代を
     * 持ち直していなければ、続けての保存は競合として断られる。
     */
    await page.getByLabel(TITLE_LABEL).fill(`${renamed} 2`);
    /* 保存が通れば一覧へ戻る。競合していないことは、戻れたことで分かる */
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();
    await expect(page.getByText(CONFLICT_HEADING)).toHaveCount(0);
  });
});
