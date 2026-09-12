import type { Locator, Page } from '@playwright/test';

import { stack } from '../support/config.ts';
import { capture } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 主要な導線の置き方（#357）。
 *
 * <p>
 * 広い幅では本文の脇へ縦に置き、狭い幅では本文の上へ戻す。**幅で形が変わること自体が仕様**のため、
 * 同じ検査を両方の幅で回し、位置関係で確かめる。見た目のクラス名ではなく座標で見るのは、当て方が
 * 変わっても「脇にある」「上にある」は変わらないため。
 * </p>
 *
 * <p>
 * 追従（`position: sticky`）は広い幅の導線だけが持つ。スクロールしてもそこに残ることを、位置の変化で
 * 確かめて証跡に残す。
 * </p>
 */

/** 導線の集まり。役割で指す（画面ごとに文言も並びも違うため） */
const NAV_LABEL = '主要な導線';

/** 広い幅。project の既定（1280×800）と揃える */
const WIDE = { width: 1280, height: 800 };

/** 狭い幅。`responsive` の検査と揃える（iPhone 12 相当の 390） */
const NARROW = { width: 390, height: 844 };

/**
 * 追従を見るために要るスクロール量の下限。
 *
 * これだけ動かせない画面では、追従していてもしていなくても位置が変わらず、検査が何も言っていない
 * ことになる。記事の一覧（1ページ20件）はどちらの画面でもこれを超える。
 */
const MIN_SCROLL_PX = 300;

/** 管理画面を開く操作。文言は画面の実装が持つ */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

const navOf = (page: Page): Locator => page.getByRole('navigation', { name: NAV_LABEL });

/** 要素の位置。無いものは位置を持たないため、続けずに落とす */
const boxOf = async (locator: Locator): Promise<{ x: number; y: number; height: number }> => {
  const box = await locator.boundingBox();
  return box === null ? Promise.reject(new Error('要素が画面に無いため、位置を取れません')) : box;
};

/** 画面が横へはみ出した量。0 でなければ横スクロールが出ている */
const horizontalOverflowOf = async (page: Page): Promise<number> =>
  page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);

/**
 * 下端までスクロールし、実際に動いた量を返す。
 *
 * 決め打ちの量を指定すると、その高さを持たない画面では黙って0になり、追従の有無を問えないまま緑になる。
 */
const scrollToBottom = async (page: Page): Promise<number> => {
  await page.evaluate(() => {
    window.scrollTo({ top: document.body.scrollHeight, behavior: 'instant' });
  });
  return page.evaluate(() => window.scrollY);
};

/** 鍵を入れて管理画面の記事一覧が出た状態にする（一覧が出ていないと、スクロールする高さが無い） */
const openAdminArticles = async (page: Page): Promise<void> => {
  await page.goto(`${stack.adminBaseUrl}/articles`);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

test.describe('広い幅の導線', () => {
  test.use({ viewport: WIDE });

  test('公開サイトでは、導線が本文の脇に出る', async ({ page }) => {
    await page.goto('/articles');

    const nav = await boxOf(navOf(page));
    const main = await boxOf(page.getByRole('main'));

    /* 脇にあるとは、導線の右端が本文の左端より左にあること。上下の関係では言えない */
    expect(nav.x).toBeLessThan(main.x);
    await capture(page, '80-wide-public-nav');
  });

  test('公開サイトの導線は、スクロールしても残る', async ({ page }) => {
    await page.goto('/articles');

    const before = await boxOf(navOf(page));
    const scrolled = await scrollToBottom(page);
    expect(scrolled).toBeGreaterThan(MIN_SCROLL_PX);

    /*
     * 追従しているとは、スクロールした分だけ上へ流れていないこと。流れていれば、この差はスクロール量と
     * ほぼ同じになる。
     */
    const after = await boxOf(navOf(page));
    expect(after.y).toBeGreaterThan(before.y - scrolled);
    await expect(navOf(page)).toBeInViewport();

    /* ヘッダー（サイト名）は流す。追従するものが増えるほど読む高さが減るため（#357） */
    await expect(page.getByRole('banner')).not.toBeInViewport();
    await capture(page, '81-wide-public-nav-scrolled');
  });

  test('管理画面でも、導線が本文の脇に出る', async ({ page }) => {
    await openAdminArticles(page);

    const nav = await boxOf(navOf(page));
    const main = await boxOf(page.getByRole('main'));

    expect(nav.x).toBeLessThan(main.x);
    await capture(page, '82-wide-admin-nav');
  });

  test('管理画面の導線も、スクロールしても残る', async ({ page }) => {
    await openAdminArticles(page);

    const before = await boxOf(navOf(page));
    const scrolled = await scrollToBottom(page);
    expect(scrolled).toBeGreaterThan(MIN_SCROLL_PX);

    const after = await boxOf(navOf(page));
    expect(after.y).toBeGreaterThan(before.y - scrolled);
    await expect(navOf(page)).toBeInViewport();
    await capture(page, '83-wide-admin-nav-scrolled');
  });
});

test.describe('狭い幅の導線', () => {
  test.use({ viewport: NARROW });

  test('公開サイトでは、導線が本文の上に戻る', async ({ page }) => {
    await page.goto('/articles');

    const nav = await boxOf(navOf(page));
    const main = await boxOf(page.getByRole('main'));

    /* 上にあるとは、導線の下端が本文の上端より上にあること */
    expect(nav.y + nav.height).toBeLessThanOrEqual(main.y);
    expect(await horizontalOverflowOf(page)).toBe(0);
    await capture(page, '84-narrow-public-nav');
  });

  test('狭い幅では、導線は追従しない', async ({ page }) => {
    await page.goto('/articles');

    const before = await boxOf(navOf(page));
    const scrolled = await scrollToBottom(page);
    expect(scrolled).toBeGreaterThan(MIN_SCROLL_PX);

    /*
     * 貼り付いた帯は画面の高さを削る。狭い画面ほどその割合が大きいため、追従させない（#357）。
     * スクロールした分だけ上へ流れていることで見る。
     */
    const after = await boxOf(navOf(page));
    expect(after.y).toBeCloseTo(before.y - scrolled, 0);
  });

  test('管理画面でも、導線が本文の上に戻る', async ({ page }) => {
    await openAdminArticles(page);

    const nav = await boxOf(navOf(page));
    const main = await boxOf(page.getByRole('main'));

    expect(nav.y + nav.height).toBeLessThanOrEqual(main.y);
    expect(await horizontalOverflowOf(page)).toBe(0);
    await capture(page, '85-narrow-admin-nav');
  });
});
