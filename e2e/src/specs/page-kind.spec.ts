import type { Locator, Page } from '@playwright/test';

import { findAlbumByCatalogNumber, findArticleByTitle } from '../support/admin-api.ts';
import { albumArticle, quiet, showcase } from '../support/build-fixtures.ts';
import { captureWhole } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 作品のページと記事のページの見分け（#353）。
 *
 * <p>
 * 内容が近く、見出しの下の1行目まで同じ形になる。読み手が「いま作品そのものを見ているのか、作品に
 * ついて書かれた記事を見ているのか」を掴む手がかりとして、本文に沿う印を置く。
 * </p>
 *
 * <p>
 * ここで見るのは**取り違えないこと**である。片方に印があることだけを見ると、両方に同じ印が出ていても
 * 通る。それぞれのページで、相手の印が出ていないことまで確かめる。
 * </p>
 *
 * <p>
 * 文言の有無だけでは、印の**形**が変わっても通ってしまう。横倒しの文言・本文の端から端まで通る縦の罫・
 * 見出しの分だけ下げた文言の始まりは、いずれも #353 の判断そのものであるため、そこまで見る。
 * </p>
 */

/** 印の文言。画面の実装が持つ */
const WORK_MARK = 'Work';
const ARTICLE_MARK = 'Article';

/** 記事の詳細に出してはいけない種別の文言（#346） */
const ALBUM_TYPE_LABEL = '作品紹介';

const albumPathOf = async (catalogNumber: string): Promise<string> => {
  const album = await findAlbumByCatalogNumber(catalogNumber);

  return album === undefined
    ? Promise.reject(new Error(`シードした作品が見つかりません: ${catalogNumber}`))
    : `/albums/${album.albumId}`;
};

const articlePathOf = async (title: string): Promise<string> => {
  const article = await findArticleByTitle(title);

  return article === undefined
    ? Promise.reject(new Error(`シードした記事が見つかりません: ${title}`))
    : `/articles/${article.articleId}`;
};

/** ページの本体。印はこの端から端までに沿う */
const pageBodyOf = (page: Page) => page.locator('article');

/** 印の文言。罫はこれを囲む区画が引く */
const markOf = (page: Page, mark: string) => pageBodyOf(page).getByText(mark, { exact: true });

/** 印の罫を持つ区画 */
const ruleOf = (mark: Locator) => mark.locator('xpath=..');

/** 本文の側の段。印の区画と横に並ぶ */
const textColumnOf = (page: Page) => pageBodyOf(page).locator(':scope > div').last();

/**
 * 印の組み方。文言が横倒しの斜体で、罫が縦に立っているかを見る。
 *
 * 罫は文言の下線として引くため、横倒しにすると物理的には左右のどちらかに来る。上下の罫が無いことまで
 * 見ないと、横線を足しただけの形が通る。
 *
 * 斜体は書体が実体を持たず、処理系が傾けて描く。指定が落ちても字が消えるわけではないため、指定そのもの
 * を見る。
 */
interface MarkLayout {
  readonly writingMode: string;
  readonly fontStyle: string;
  readonly rules: readonly string[];
}

const layoutOf = async (mark: Locator): Promise<MarkLayout> => ({
  writingMode: await mark.evaluate((element) => getComputedStyle(element).writingMode),
  fontStyle: await mark.evaluate((element) => getComputedStyle(element).fontStyle),
  rules: await ruleOf(mark).evaluate((element) => {
    const style = getComputedStyle(element);
    return [
      style.borderTopWidth,
      style.borderRightWidth,
      style.borderBottomWidth,
      style.borderLeftWidth,
    ];
  }),
});

/** 位置を見るための枠。画面に出ていないものは測れないため、そこで止める */
const boxOf = async (locator: Locator) => {
  const box = await locator.boundingBox();

  return box === null ? Promise.reject(new Error('要素が画面に出ていません')) : box;
};

/** 描画の丸めの分だけ許す */
const TOLERANCE = 1;

test.describe('作品のページと記事のページの見分け', () => {
  test('作品の詳細は、そのページが作品のものだと名乗る', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    await expect(pageBodyOf(page)).toContainText(WORK_MARK);
    await expect(pageBodyOf(page)).not.toContainText(ARTICLE_MARK);

    await captureWhole(page, '05b-album-detail-kind');
  });

  test('記事の詳細は、そのページが記事のものだと名乗る', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    await expect(pageBodyOf(page)).toContainText(ARTICLE_MARK);
    await expect(pageBodyOf(page)).not.toContainText(WORK_MARK);

    /*
     * 出すのはページの種類であって、記事の種別ではない（#346 が落としたのは後者）。分類名が戻って
     * いないことを、同じ画面で見る。
     */
    await expect(page.getByText(ALBUM_TYPE_LABEL, { exact: true })).toHaveCount(0);

    await captureWhole(page, '10c-article-detail-kind');
  });

  test('印は、横倒しの文言を縦の罫が受ける形で置く', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    expect(await layoutOf(markOf(page, WORK_MARK))).toEqual({
      writingMode: 'vertical-rl',
      fontStyle: 'italic',
      rules: ['0px', '2px', '0px', '0px'],
    });
  });

  test('罫は、見出しの脇だけでなく本文の端から端まで通る', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    const rule = await boxOf(ruleOf(markOf(page, WORK_MARK)));
    const body = await boxOf(textColumnOf(page));

    expect(rule.y).toBeLessThanOrEqual(body.y + TOLERANCE);
    expect(rule.y + rule.height).toBeGreaterThanOrEqual(body.y + body.height - TOLERANCE);
  });

  test('文言は、見出しの高さの分を空けてから始まる', async ({ page }) => {
    await page.goto(await albumPathOf(showcase.catalogNumber));

    const mark = await boxOf(markOf(page, WORK_MARK));
    const heading = await boxOf(page.getByRole('heading', { level: 1 }));

    expect(mark.y).toBeGreaterThanOrEqual(heading.y + heading.height);
  });

  test('一覧のカードには、印を置かない', async ({ page }) => {
    await page.goto('/albums');
    await expect(page.getByRole('link').filter({ hasText: quiet.title })).not.toContainText(
      WORK_MARK,
    );

    await page.goto('/articles');
    await expect(page.getByRole('link').filter({ hasText: albumArticle.title })).not.toContainText(
      ARTICLE_MARK,
    );
  });
});
