import { attributeOf } from '../support/attributes.ts';
import { capture } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { DEFAULT_PREVIEW_IMAGE, FAVICON_PATH, PREVIEW_IMAGE_SIZE } from '../support/site-marks.ts';

/**
 * サイトの記号（#341）。
 *
 * <p>
 * 印とリンクプレビューの既定画像は、どのページにも同じものが出る。見るのは宣言されていることだけでは
 * なく、**その先に実体が届くこと**——経路の綴りが合っていて配信に無い状態は、画面を見ても分からない
 * （タブの印は画面の外、リンクプレビューは他所のサービスが読む）。
 * </p>
 */

test.describe('サイトの印', () => {
  test('ページが印を指し、その実体が届く', async ({ page }) => {
    await page.goto('/');

    const href = await attributeOf(page.locator('link[rel="icon"]'), 'href');
    expect(href).toBe(FAVICON_PATH);

    const fetched = await page.request.get(href);
    expect(fetched.status()).toBe(200);
    expect(fetched.headers()['content-type']).toContain('image/svg+xml');
  });
});

test.describe('リンクプレビューの既定画像', () => {
  test('作品から採る画像を持たないページでは、既定の画像になる', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute(
      'content',
      'summary_large_image',
    );

    /*
     * 読む側は他所のサービスのため、絶対URLで出す必要がある。基準はビルド時に渡す site で、
     * ドメインはコードへ書かない（#129）。ここでは末尾の経路だけを見る。
     */
    const image = await attributeOf(page.locator('meta[property="og:image"]'), 'content');
    expect(image).toContain(DEFAULT_PREVIEW_IMAGE);
    expect(image.startsWith('http')).toBe(true);

    const fetched = await page.request.get(image);
    expect(fetched.status()).toBe(200);
  });

  test('既定の画像は、リンクプレビューが想定する比で描かれる', async ({ page }) => {
    await page.goto(DEFAULT_PREVIEW_IMAGE);

    /*
     * ブラウザは画像そのものの経路を開くと `img` 1つの文書にする。実寸で見るのは、届いたバイト列が
     * 本当にその大きさの絵であることまで確かめるため。
     */
    const image = page.locator('img');
    await expect(image).toHaveJSProperty('naturalWidth', PREVIEW_IMAGE_SIZE.width);
    await expect(image).toHaveJSProperty('naturalHeight', PREVIEW_IMAGE_SIZE.height);

    /* 印そのものが見どころのため、画像の文書を撮る（タブの印は画面の外で撮れない） */
    await capture(page, '01c-site-mark');
  });
});
