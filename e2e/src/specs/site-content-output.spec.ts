import type { Page } from '@playwright/test';

import { stack } from '../support/config.ts';
import { captureFocused, captureWhole } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 編集する文言と、公開サイトに出るものの繋がり（#383 / #343 / #230）。
 *
 * <p>
 * **この実行の中で「直して、出るのを見る」ことはできない。** 公開サイトは静的出力で、組み立てた時点の
 * 内容がそのまま HTML になる（DECISIONS 24）。組み立てのあとに直しても、その回の画面は変わらない。
 * 直したものを配るところまで繋ぐ経路は #374 が持つ。
 * </p>
 *
 * <p>
 * ここで見るのは、**管理画面が編集の欄に見せている値そのものが、公開サイトに出ている**ことである。
 * 期待値にフィクスチャの定数を使わない——定数どうしを突き合わせると、画面がどちらも別の値を出していても
 * 両方が通る。片方の画面から読み取った文字列を、もう片方で確かめる。
 * </p>
 */

/** 鍵の入力欄と、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 入力欄のラベルと操作。文言は画面の実装が持つ */
const CONTENT_LABEL = '本文';
const EDIT_LABEL = '編集する';

/**
 * 管理画面が持つ、そのキーの本文。
 *
 * 一覧の抜粋ではなく編集の欄から読む。抜粋は省略されることがあり、**運営者が直す対象そのもの**は
 * 編集の欄にある。
 */
const editedContentOf = async (page: Page, key: string): Promise<string> => {
  await page
    .getByRole('row')
    .filter({ hasText: key })
    .getByRole('button', { name: EDIT_LABEL })
    .click();

  return page.getByLabel(CONTENT_LABEL).inputValue();
};

test.describe('編集した文言と、公開サイトに出るもの', () => {
  test('管理画面が編集の欄に見せている値が、公開サイトのその場所に出ている', async ({ page }) => {
    await page.goto(`${stack.adminBaseUrl}/site-contents`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();
    await expect(page.getByRole('table')).toBeVisible();

    const siteName = await editedContentOf(page, 'site.name');
    const copyrightHolder = await editedContentOf(page, 'footer.copyright.holder');

    /* 直す対象が画面に出ている状態で撮る。読み取った値がどこから来たのかを証跡から追えるようにする */
    await captureFocused(page, page.locator('form'), '67a-admin-site-content-source');

    await page.goto(stack.siteBaseUrl);

    /* サイト名はヘッダーのロゴに出る（#358） */
    await expect(page.locator('header')).toContainText(siteName);

    /*
     * コピーライトの主体は末尾に出る。年と記号は画面が組み立てるため（#343）、データ側が持つのは
     * 主体だけである。
     */
    await expect(page.locator('footer')).toHaveText(
      new RegExp(`^© \\d{4} ${copyrightHolder}$`, 'u'),
    );

    /*
     * 出る場所はページの両端（ヘッダーのロゴと末尾）で、1画面に収まらない。どちらかへ寄せると、
     * 確かめているものの片方しか写らない（#369）。
     */
    await captureWhole(page, '67b-public-site-content-output');
  });
});
