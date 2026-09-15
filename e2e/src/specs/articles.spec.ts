import type { Locator, Page } from '@playwright/test';

import { findArticleByTitle } from '../support/admin-api.ts';
import { attributeOf } from '../support/attributes.ts';
import {
  albumArticle,
  coverlessArticle,
  draftArticle,
  pagination,
  plainArticle,
  quietArticle,
  showcase,
  showcaseTracks,
} from '../support/build-fixtures.ts';
import { coverImageAsset } from '../support/cover-image.ts';
import { capture, captureFocused, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import { DEFAULT_PREVIEW_IMAGE } from '../support/site-marks.ts';

/**
 * 公開サイトの記事（#123）のジャーニー。
 *
 * 見るのは #197 が確定した内容が画面に出ているか。記事の種別ラベルは契約（列挙子名）ではなく画面が
 * 決める文言のため、シナリオ側に置く。
 */

/** 詳細には出さないことを確かめるための種別ラベル（#346） */
const ALBUM_TYPE_LABEL = '作品紹介';

/** ページ送りの導線と、その区画。文言は画面の実装が持つ */
const NEXT_PAGE_LINK = '次のページ';
const PREVIOUS_PAGE_LINK = '前のページ';
const PAGINATION_LABEL = 'ページ送り';

const paginationIn = (page: Page): Locator =>
  page.getByRole('navigation', { name: PAGINATION_LABEL });

/**
 * ページ送りの現在地（`2 / 3`）の左端。
 *
 * 端の有無で位置が動かないことを見るために取る。動くかどうかは、読み手が同じ場所を見続けられるかの
 * 話なので、要素の有無ではなく座標でしか確かめられない。
 */
const currentPagePositionOf = async (page: Page): Promise<number> => {
  const box = await paginationIn(page).locator('span').boundingBox();

  return box === null ? Promise.reject(new Error('ページ送りの現在地が画面にありません')) : box.x;
};

/** 404 の見出し。定型文のため画面の実装が持つ（#230） */
const NOT_FOUND_HEADING = 'ページが見つかりません';

/** 額の整形が出す通貨の記号。額が出ていないことは、記号の不在でしか言えない */
const CURRENCY_SIGN = '￥';

/**
 * 1ページ目に並ぶはずのタイトル。
 *
 * 公開の順序がそのまま並び順になる（公開日の降順）。最後に公開した作品紹介・ノートが先頭へ来て、
 * 詰め物は番号の大きい方から続く。1ページに収まらない最後の1件（詰め物の1番）は2ページ目へ送られる。
 */
const firstPageTitles = [
  albumArticle.title,
  plainArticle.title,
  quietArticle.title,
  coverlessArticle.title,
  ...Array.from({ length: pagination.filler - 1 }, (_unused, index) =>
    pagination.titleOf(pagination.filler - index),
  ),
];

const articlePathOf = async (title: string): Promise<string> => {
  const article = await findArticleByTitle(title);
  return article === undefined
    ? Promise.reject(new Error(`シードした記事が見つかりません: ${title}`))
    : `/articles/${article.articleId}`;
};

test.describe('記事の一覧', () => {
  test('一覧から詳細へたどり、記事を読める', async ({ page }) => {
    await page.goto('/articles');

    const card = page.getByRole('link').filter({ hasText: albumArticle.title });
    await expect(card).toBeVisible();
    await expect(card).toContainText(albumArticle.introShort);
    await capture(page, '08-articles-list');

    await clickWithEvidence(page, card, '09-articles-list-open-detail');

    await expect(page.getByRole('heading', { level: 1, name: albumArticle.title })).toBeVisible();

    /* 種別は詳細に出さない（#346）。完全一致で見るのは、同じ語がフィクスチャのタイトルにも現れるため */
    await expect(page.getByText(ALBUM_TYPE_LABEL, { exact: true })).toHaveCount(0);
    /* 記事の見出しの中の日付を見る。参照先の作品も初出イベントの日付を持つため */
    await expect(page.locator('[data-article-header] time[datetime]')).toBeVisible();
    await expect(
      page.getByRole('heading', { level: 2, name: albumArticle.body.heading }),
    ).toBeVisible();
    await expect(page.getByText(albumArticle.body.lead)).toBeVisible();
    await capture(page, '10-article-detail');
  });

  test('1ページ目に、公開日の降順で20件が並ぶ', async ({ page }) => {
    await page.goto('/articles');

    const titles = await page.getByRole('heading', { level: 2 }).allInnerTexts();

    expect(titles).toEqual(firstPageTitles);
  });

  test('次のページから残りを辿れる', async ({ page }) => {
    await page.goto('/articles');

    const next = page.getByRole('link', { name: NEXT_PAGE_LINK });
    await clickWithEvidence(page, next, '12-articles-next-page');

    /*
     * 見出しを読む前に、遷移が終わったことを URL で確かめる。要素の一覧を取る操作は「まだ無い」を
     * 待たずに空を返すため、遷移の途中で読むと 0 件を正解として受け取ってしまう。
     */
    await page.waitForURL(/\/articles\/page\/2$/u);

    /* 公開した記事は1ページを1件だけ超える。2ページ目には最初に公開した1件だけが残る */
    const titles = await page.getByRole('heading', { level: 2 }).allInnerTexts();
    expect(titles).toEqual([pagination.titleOf(1)]);
  });

  test('下書きは一覧に出ない', async ({ page }) => {
    await page.goto('/articles');

    await expect(page.getByText(draftArticle.title)).toHaveCount(0);
  });

  test('ページ送りの端では、たどれない側を出さず、現在地の位置も動かさない', async ({ page }) => {
    await page.goto('/articles');

    /* 1ページ目に「前」は無い。押せない文字としても残さない（#343） */
    await expect(paginationIn(page).getByText(PREVIOUS_PAGE_LINK)).toHaveCount(0);
    await expect(paginationIn(page).getByRole('link', { name: NEXT_PAGE_LINK })).toBeVisible();

    const atFirstPage = await currentPagePositionOf(page);
    await captureFocused(page, paginationIn(page), '12a-articles-pagination-first');

    await page.goto('/articles/page/2');

    /* 最後のページに「次」は無い */
    await expect(paginationIn(page).getByRole('link', { name: PREVIOUS_PAGE_LINK })).toBeVisible();
    await expect(paginationIn(page).getByText(NEXT_PAGE_LINK)).toHaveCount(0);

    /*
     * 端の有無が変わっても現在地は同じ場所にある。詰める形にすると、ページを送るたびに現在地が
     * 左右へ動く——それが出さない側を「場所だけ空ける」形にした理由である。
     */
    expect(await currentPagePositionOf(page)).toBe(atFirstPage);
    await captureFocused(page, paginationIn(page), '12b-articles-pagination-last');
  });

  test('記事のカードの画像は、参照先の作品がカバー画像を持つときだけ出る', async ({ page }) => {
    await page.goto('/articles');

    /* 画像の出所は記事ではなく参照先の作品。`quiet` はカバー画像を持つ（#377） */
    const withCover = page.getByRole('link').filter({ hasText: quietArticle.title });
    await expect(withCover.locator('img')).toHaveJSProperty('naturalWidth', coverImageAsset.width);

    /*
     * 作品を参照していても、参照先が画像を持たなければ出ない。**画像の有無を決めるのは記事が参照を
     * 持つかどうかではなく、参照先の作品が画像を持つかどうか**である。参照の有無だけで対比すると、
     * 「作品紹介の記事なら何か出す」という実装でも通ってしまう。
     */
    const referencingWithoutCover = page
      .getByRole('link')
      .filter({ hasText: coverlessArticle.title });
    await expect(referencingWithoutCover.locator('img')).toHaveCount(0);

    /* 作品を参照しない記事のカードにも、画像そのものを置かない */
    const withoutReference = page.getByRole('link').filter({ hasText: plainArticle.title });
    await expect(withoutReference.locator('img')).toHaveCount(0);

    /* 3枚が同じ絵に並ぶ。出る側とその2通りの出ない側を、対比として1枚に収める */
    await captureFocused(page, withCover, '08a-articles-list-cover');
  });
});

test.describe('記事の詳細', () => {
  test('タグが出て、ショート紹介文は出ない', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    await expect(page.getByText(albumArticle.tags[0])).toBeVisible();
    await expect(page.getByText(albumArticle.tags[1])).toBeVisible();

    /* ショート紹介文は一覧のためのもので、詳細には出さない（#197） */
    await expect(page.getByText(albumArticle.introShort)).toHaveCount(0);
  });

  test('作品紹介の記事内に試聴・本文・曲目・イベント・価格が順に展開される', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));
    await expect(page.getByRole('heading', { level: 2, name: showcase.title })).toBeVisible();
    await expect(page.locator('[data-album-tracks]')).toContainText(
      showcaseTracks.titledWithTune.name,
    );
    expect(
      await page
        .locator(
          '[data-album-audio], [data-article-body], [data-album-tracks], [data-album-event], [data-album-price]',
        )
        .evaluateAll((elements) =>
          elements.map((element) =>
            element.getAttributeNames().find((name) => name.startsWith('data-')),
          ),
        ),
    ).toEqual([
      'data-album-audio',
      'data-article-body',
      'data-album-tracks',
      'data-album-event',
      'data-album-price',
    ]);
  });

  test('作品を紹介する記事に、参照先の作品のスペース情報と基準額が出る', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    const reference = page.locator('[data-public-album]');
    await expect(reference).toContainText(showcase.eventName);
    await expect(reference).toContainText(showcase.eventPlace);
    await expect(reference).toContainText(showcase.eventSpaceNumber);
    await expect(reference).toContainText(showcase.basePriceText);
    /* 記事の詳細（10）と同じ画面の別の見どころのため、その枝番に置く */
    await captureFocused(
      page,
      reference.locator('[data-album-event]'),
      '10a-article-album-event-price',
    );
  });

  test('額を持たない作品を紹介する記事には、額の区画が出ない', async ({ page }) => {
    await page.goto(await articlePathOf(quietArticle.title));

    const reference = page.locator('[data-public-album]');
    await expect(reference).toBeVisible();

    /* 通貨の記号で見る。額そのものは作品ごとに違い、出ないことは記号の不在でしか言えない */
    await expect(reference).not.toContainText(CURRENCY_SIGN);
  });

  test('作品を紹介する記事のリンクプレビューは、参照先の作品のもの', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    /* 参照先の作品は外部音源を持つため、プレイヤーカードになる（#197） */
    await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute('content', 'player');

    const playerUrl = await page.locator('meta[name="twitter:player"]').getAttribute('content');
    expect(playerUrl).toContain(encodeURIComponent(showcase.audioUrl));
  });

  test('音源を持たない作品を紹介する記事のリンクプレビューは、その作品のカバー画像になる', async ({
    page,
  }) => {
    await page.goto(await articlePathOf(quietArticle.title));

    /* 参照先が音源を持たないため、プレイヤーカードではなくカバー画像のカードになる（#197） */
    await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute(
      'content',
      'summary_large_image',
    );

    const reference = page.locator('[data-public-album]');
    const cover = reference.locator('img');
    await expect(cover).toHaveJSProperty('naturalWidth', coverImageAsset.width);

    /* 指しているのは、参照の区画に出ているのと同じ画像である */
    const previewImage = await attributeOf(page.locator('meta[property="og:image"]'), 'content');
    expect(previewImage).toContain(await attributeOf(cover, 'src'));

    await captureFocused(page, reference, '10b-article-album-reference-cover');
  });

  test('作品を参照しない記事には作品への導線が出ず、リンクプレビューは既定の画像になる', async ({
    page,
  }) => {
    await page.goto(await articlePathOf(plainArticle.title));

    await expect(page.locator('[data-public-album]')).toHaveCount(0);

    /*
     * 参照が無くてもリンクプレビューは空にしない（#341）。参照先から採る画像が無いだけで、サイトの
     * 記号は出せる。プレイヤーカードにならないことは、その札の不在で見る。
     */
    await expect(page.locator('meta[name="twitter:player"]')).toHaveCount(0);
    await expect(page.locator('meta[property="og:image"]')).toHaveAttribute(
      'content',
      new RegExp(`${DEFAULT_PREVIEW_IMAGE}$`, 'u'),
    );
  });

  test('プレーンテキストの本文は記法として解釈されない', async ({ page }) => {
    await page.goto(await articlePathOf(plainArticle.title));

    await expect(page.getByText(plainArticle.body)).toBeVisible();
    await expect(page.locator('strong')).toHaveCount(0);
  });

  test('未存在と下書きは、同じ 404 のページになる', async ({ page }) => {
    /*
     * 未存在と非公開を区別せず、どちらも同じ 404 を返す（#197。下書きの存在を漏らさない）。静的出力の
     * ため下書きのページはそもそも組まれず、配信が 404 のページを返す。
     */
    const missing = await page.goto('/articles/not-a-real-article');
    expect(missing?.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1, name: NOT_FOUND_HEADING })).toBeVisible();
    await capture(page, '13-not-found');

    const draft = await page.goto(await articlePathOf(draftArticle.title));
    expect(draft?.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1, name: NOT_FOUND_HEADING })).toBeVisible();
  });
});
