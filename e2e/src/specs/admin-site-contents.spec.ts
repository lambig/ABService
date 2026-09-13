import type { Locator, Page } from '@playwright/test';

import { siteContent } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture, captureFocused } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 管理画面のサイトの文言（#383）のジャーニー。
 *
 * <p>
 * #230 は「初期データは持たない、管理画面から入れる」と決めたが、その入り口が無かった。ここで見るのは、
 * 文言がこの画面から実際に入って残ることである。応答は差し替えず、実APIへ保存する（#164）。
 * </p>
 *
 * <p>
 * 文言には削除の経路が無い（バックエンドも持たない）。検査のためだけに作るキーは**固定のものを1つ**
 * 使い回す——実行ごとに新しいキーを作ると、ローカルのデータベースに消せないものが積み上がる。
 * 保存されたことは、本文へ実行ごとの印を入れて確かめる。
 * </p>
 */

/** 鍵の入力欄と、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 入力欄のラベル。文言は画面の実装が持つ */
const KEY_LABEL = 'キー';
const FORMAT_LABEL = '形式';
const CONTENT_LABEL = '本文';
const SAVE_LABEL = '保存する';
const EDIT_LABEL = '編集する';

/** 検査のためだけに使うキー。公開サイトはこれを読まない */
const SCRATCH_KEY = 'e2e.scratch.text';

/** 形式の表示。列挙子名ではなく画面に出る文言 */
const MARKDOWN_LABEL = 'Markdown';

const SITE_CONTENT_PATH = `${stack.adminBaseUrl}/site-contents`;

const openSiteContents = async (page: Page): Promise<void> => {
  await page.goto(SITE_CONTENT_PATH);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

/**
 * 同じタブで開き直す。
 *
 * 鍵は同じタブの間は保持されるため、入れ直す欄は出ない。読み直しそのものが、鍵が覚えられていることも
 * 兼ねて見せる。
 */
const reopenSiteContents = async (page: Page): Promise<void> => {
  await page.goto(SITE_CONTENT_PATH);
  await expect(page.getByRole('table')).toBeVisible();
};

/** キーで一覧の行を指す */
const rowOf = (page: Page, key: string): Locator => page.getByRole('row').filter({ hasText: key });

/** 欄ごとのまとまり。エラーがその欄の下に出ていることを見るため、ラベルではなくまとまりを経由する */
const fieldOf = (page: Page, path: string): Locator => page.locator(`[data-field="${path}"]`);

test.describe('管理画面のサイトの文言', () => {
  test('鍵を入れると、登録済みの文言がキーごとに並ぶ', async ({ page }) => {
    await openSiteContents(page);

    /* 公開サイトが読むキー。値の出所はシード（`build-fixtures.ts`）で、ここには書き写さない */
    await expect(rowOf(page, 'site.name')).toContainText(siteContent.name);
    await expect(rowOf(page, 'footer.copyright.holder')).toContainText(siteContent.copyrightHolder);

    await capture(page, '67-admin-site-contents');
  });

  test('登録済みの文言を選ぶと、本文が編集の欄に入る', async ({ page }) => {
    await openSiteContents(page);

    await rowOf(page, 'site.name').getByRole('button', { name: EDIT_LABEL }).click();

    await expect(page.getByLabel(KEY_LABEL)).toHaveValue('site.name');
    await expect(page.getByLabel(CONTENT_LABEL)).toHaveValue(siteContent.name);

    /*
     * 登録済みのキーは変えられない。書き換えられると別のキーとして新しく作られ（保存は upsert）、
     * 消す経路も無いため書き間違いがそのまま残る。
     */
    await expect(page.getByLabel(KEY_LABEL)).toHaveAttribute('readonly', '');
  });

  test('キーと本文を入れて保存すると、一覧に並び、読み直しても残っている', async ({ page }) => {
    const written = `E2E で保存した文言 ${String(Date.now())}`;

    await openSiteContents(page);

    await page.getByLabel(KEY_LABEL).fill(SCRATCH_KEY);
    await page.getByLabel(CONTENT_LABEL).fill(written);
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    /* 保存できた文言は、引き直さずに一覧へ入る（保存の応答が保存後の内容を返すため） */
    await expect(rowOf(page, SCRATCH_KEY)).toContainText(written);
    await captureFocused(page, rowOf(page, SCRATCH_KEY), '68-admin-site-content-saved');

    /*
     * 画面の中の一覧が変わっただけかもしれない。**保存されたことは、読み直して初めて言える。**
     */
    await reopenSiteContents(page);
    await expect(rowOf(page, SCRATCH_KEY)).toContainText(written);
  });

  test('キーの形式が受け付けられないときは、その欄にエラーが出る', async ({ page }) => {
    await openSiteContents(page);

    /* 形式の規則はバックエンドが持つ（小文字の区切りを2つ以上）。画面は同じ判定を持たない */
    await page.getByLabel(KEY_LABEL).fill('NotAKey');
    await page.getByLabel(CONTENT_LABEL).fill('E2E の本文');
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(fieldOf(page, 'key').getByRole('alert')).toBeVisible();
    await expect(fieldOf(page, 'content').getByRole('alert')).toHaveCount(0);

    await captureFocused(page, fieldOf(page, 'key'), '69-admin-site-content-key-error');
  });

  test('Markdown を選ぶと、公開サイトと同じ描画でプレビューが出る', async ({ page }) => {
    await openSiteContents(page);

    await page.getByLabel(CONTENT_LABEL).fill('## E2E の見出し');
    await page.getByLabel(FORMAT_LABEL).selectOption({ label: MARKDOWN_LABEL });

    const preview = page.locator('[data-preview]');
    await expect(preview.getByRole('heading', { level: 2, name: 'E2E の見出し' })).toBeVisible();

    /* プレーンテキストへ戻すと記法として解釈しない。プレビューの区画ごと出ない */
    await page.getByLabel(FORMAT_LABEL).selectOption({ index: 0 });
    await expect(preview).toHaveCount(0);
  });
});
