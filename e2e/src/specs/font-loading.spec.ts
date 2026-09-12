import { setTimeout as delay } from 'node:timers/promises';

import { siteContent } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture, captureWhileCovered } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 書体が届くまでの覆い（#361）。
 *
 * <p>
 * 公開サイトは書体を外部から読み込む（#341）。届いた瞬間に字面が入れ替わるのを見せないため、それまで
 * 地の色で覆う。ここで見るのは**覆いが出ること**と、**届かなくても内容へ辿り着けること**の両方で、
 * 後者が崩れると画面が永久に塞がる。
 * </p>
 */

/** 書体の定義（stylesheet）の取得元 */
const FONT_STYLESHEET = '**://fonts.googleapis.com/**';

/**
 * 書体の実体（フォントファイル）の取得元。
 *
 * 覆いが働くのはここを待つ間になる。stylesheet の側を止めるとブラウザが描画そのものを止めてしまい、
 * 画面に出るのは覆いではなくブラウザの白紙になる（覆いを見るには描画が進んでいる必要がある）。
 */
const FONT_FILE = '**://fonts.gstatic.com/**';

/** 覆いの要素。実装が持つクラスで指す（見た目そのものを検査するため） */
const COVER = '.font-loading-cover';

/** 応答を返さない取得元。要求は保留のまま残り、覆いが外れる契機は画面側の上限だけになる */
const stall = (): Promise<never> => new Promise(() => undefined);

/**
 * 届く側を見るときの遅れ。
 *
 * 覆いが出ている状態を観測できるだけの間を置き、画面側の上限（3秒）には遠く届かない長さにする。
 */
const SLOW_FONT_MS = 1_000;

/**
 * 取得元へ届かないと分かってから内容が出るまでに許す時間。
 *
 * 画面側の上限（3秒）より**短く**する。上限まで待って外れたのでは、失敗を見て外す経路が死んでいても
 * この検査を通ってしまう。
 */
const BEFORE_CAP_MS = 2_000;

test.describe('書体が届くまでの覆い', () => {
  test('届くまでは覆う', async ({ page }) => {
    await page.route(FONT_FILE, stall);

    /*
     * 読み込みの完了を待たずに返す。既定（`load`）で待つと、保留にした取得元をそのまま待ち続ける。
     */
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    /* 覆いが出ている。証跡では地の色だけが写り、下の内容が透けないことを確かめる */
    await expect(page.locator(COVER)).toBeVisible();
    await captureWhileCovered(page, '01a-font-loading-cover');
    await expect(page.locator(COVER)).toBeVisible();

    await page.unroute(FONT_FILE);
  });

  test('届けば外し、内容を見せる', async ({ page }) => {
    await page.route(FONT_FILE, async (route) => {
      await delay(SLOW_FONT_MS);
      await route.continue();
    });

    await page.goto('/', { waitUntil: 'domcontentloaded' });

    /* 覆われた状態から外れるまでを続けて見る。最初から外れていたのでは、覆いが出たことにならない */
    await expect(page.locator(COVER)).toBeVisible();
    await expect(page.locator(COVER)).toBeHidden();
    await expect(page.getByRole('heading', { level: 1, name: siteContent.name })).toBeVisible();
    await capture(page, '01b-font-loading-uncovered');

    await page.unroute(FONT_FILE);
  });

  test('書体の実体が届かないと分かれば、待たずに内容を見せる', async ({ page }) => {
    /* 応答を差し替えるのではなくネットワークの側で塞ぐ */
    await page.route(FONT_FILE, (route) => route.abort());

    await page.goto('/', { waitUntil: 'domcontentloaded' });

    /*
     * 取得に失敗しても覆いは外れる。外れないと、取得元へ届かない回線から内容へ一切辿り着けなくなる。
     */
    await expect(page.locator(COVER)).toBeHidden({ timeout: BEFORE_CAP_MS });
    await expect(page.getByRole('heading', { level: 1, name: siteContent.name })).toBeVisible();

    await page.unroute(FONT_FILE);
  });

  test('書体の定義が届かないと分かれば、待たずに内容を見せる', async ({ page }) => {
    /* 実体を要求する前の段階で失敗する経路。stylesheet が届かなければ書体の面そのものが登録されない */
    await page.route(FONT_STYLESHEET, (route) => route.abort());

    await page.goto('/', { waitUntil: 'domcontentloaded' });

    await expect(page.locator(COVER)).toBeHidden({ timeout: BEFORE_CAP_MS });
    await expect(page.getByRole('heading', { level: 1, name: siteContent.name })).toBeVisible();

    await page.unroute(FONT_STYLESHEET);
  });
});

test.describe('書体を読み込まない画面', () => {
  test('管理画面は覆いを持たない', async ({ page }) => {
    /*
     * 管理画面は OS の標準に委ねる（#341）。覆う対象が無いため、覆いの要素そのものを持たない——
     * 持たせると、書体を読み込まない画面が理由なく塞がる。
     */
    await page.goto(stack.adminBaseUrl);

    await expect(page.locator(COVER)).toHaveCount(0);
  });
});
