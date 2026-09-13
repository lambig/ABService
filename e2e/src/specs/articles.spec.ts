import { findArticleByTitle } from '../support/admin-api.ts';
import {
  albumArticle,
  draftArticle,
  pagination,
  plainArticle,
  quiet,
  quietArticle,
  showcase,
} from '../support/build-fixtures.ts';
import { capture, captureFocused, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 公開サイトの記事（#123）のジャーニー。
 *
 * 見るのは #197 が確定した内容が画面に出ているか。記事の種別ラベルは契約（列挙子名）ではなく画面が
 * 決める文言のため、シナリオ側に置く。
 */

/** 詳細には出さないことを確かめるための種別ラベル（#346） */
const ALBUM_TYPE_LABEL = '作品紹介';

/** 参照先の作品への導線の見出し。文言は画面の実装が持つ */
const ALBUM_REFERENCE_HEADING = 'この記事の作品';

/** ページ送りの導線。文言は画面の実装が持つ */
const NEXT_PAGE_LINK = '次のページ';

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
    await expect(page.locator('article header time[datetime]')).toBeVisible();
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
});

test.describe('記事の詳細', () => {
  test('タグが出て、ショート紹介文は出ない', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    await expect(page.getByText(albumArticle.tags[0])).toBeVisible();
    await expect(page.getByText(albumArticle.tags[1])).toBeVisible();

    /* ショート紹介文は一覧のためのもので、詳細には出さない（#197） */
    await expect(page.getByText(albumArticle.introShort)).toHaveCount(0);
  });

  test('作品を紹介する記事から、その作品へたどれる', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    await expect(
      page.getByRole('heading', { level: 2, name: ALBUM_REFERENCE_HEADING }),
    ).toBeVisible();

    const reference = page.getByRole('link').filter({ hasText: showcase.title });
    await clickWithEvidence(page, reference, '11-article-open-album');

    await expect(page.getByRole('heading', { level: 1, name: showcase.title })).toBeVisible();
  });

  test('作品を紹介する記事に、参照先の作品のスペース情報と基準額が出る', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    const reference = page.getByRole('link').filter({ hasText: showcase.title });
    await expect(reference).toContainText(showcase.eventName);
    await expect(reference).toContainText(showcase.eventPlace);
    await expect(reference).toContainText(showcase.eventSpaceNumber);
    await expect(reference).toContainText(showcase.basePriceText);
    /* 記事の詳細（10）と同じ画面の別の見どころのため、その枝番に置く */
    await captureFocused(page, reference, '10a-article-album-reference-price');
  });

  test('額を持たない作品を紹介する記事には、額の区画が出ない', async ({ page }) => {
    await page.goto(await articlePathOf(quietArticle.title));

    const reference = page.getByRole('link').filter({ hasText: quiet.title });
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

  test('作品を参照しない記事には、作品への導線もリンクプレビューも出ない', async ({ page }) => {
    await page.goto(await articlePathOf(plainArticle.title));

    await expect(
      page.getByRole('heading', { level: 2, name: ALBUM_REFERENCE_HEADING }),
    ).toHaveCount(0);
    await expect(page.locator('meta[name="twitter:card"]')).toHaveCount(0);
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
