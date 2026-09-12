import { setTimeout as delay } from 'node:timers/promises';

import type { Page } from '@playwright/test';

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
 *
 * <p>
 * 覆いが長引いたときは印を出す。地の色だけの画面は壊れた画面と見分けが付かないため。印そのものも、
 * 出る時機（遅らせること）とあわせて見る。
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

/**
 * 印が出るまでの間を、画面から読む。
 *
 * 数をここへ写すと、片方だけ変えたときに検査が意味を失う。出所は `global.css` の
 * `--font-loading-hint-delay` ひとつにする。
 */
const hintDelayMsOf = async (page: Page): Promise<number> => {
  const declared = await page
    .locator(COVER)
    .evaluate((cover) =>
      getComputedStyle(cover).getPropertyValue('--font-loading-hint-delay').trim(),
    );

  /*
   * 単位は両方を読む。CSS は最小化されて配られるため、`600ms` と書いても `.6s` で届く。`ms` を先に
   * 見るのは、`s` で終わる文字列に `ms` も含まれるため。
   */
  return declared.endsWith('ms')
    ? Number.parseFloat(declared)
    : declared.endsWith('s')
      ? Number.parseFloat(declared) * MS_IN_SECOND
      : Promise.reject(new Error(`印の遅延を時間として読めません: ${declared}`));
};

const MS_IN_SECOND = 1_000;

/** 印の濃さ。印そのものは擬似要素のため、要素としては指せない */
const hintOpacityOf = async (page: Page): Promise<number> =>
  page
    .locator(COVER)
    .evaluate((cover) => Number.parseFloat(getComputedStyle(cover, '::after').opacity));

/** 印が出る前に見に行く時点。遅延のどれだけ手前で見るか */
const BEFORE_HINT_RATIO = 0.5;

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
  test('届くまでは覆い、長引けば印を出す', async ({ page }) => {
    await page.route(FONT_FILE, stall);

    /*
     * 読み込みの完了を待たずに返す。既定（`load`）で待つと、保留にした取得元をそのまま待ち続ける。
     */
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    /* 覆いが出ている。下の内容は透けない */
    await expect(page.locator(COVER)).toBeVisible();

    /*
     * 印は最初から出ているわけではない。すぐ外れる読み込みで印だけが目に残るのを避けるため遅らせる。
     * 遅延の手前で見て、まだ出ていないことを確かめる——これが無いと、遅延を 0 にしても検査は通る。
     */
    const hintDelayMs = await hintDelayMsOf(page);
    await delay(hintDelayMs * BEFORE_HINT_RATIO);
    expect(await hintOpacityOf(page)).toBe(0);

    /*
     * 長引けば印が出る。地の色だけの画面は壊れた画面と見分けが付かず、そのまま離脱する理由になる。
     * 証跡はこの状態で撮る。
     */
    await expect.poll(() => hintOpacityOf(page)).toBeGreaterThan(0);
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
