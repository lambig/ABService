import { findArticleByTitle } from '../support/admin-api.ts';
import { albumArticle, draftArticle, plainArticle, showcase } from '../support/build-fixtures.ts';
import { capture, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';

/**
 * 公開サイトの記事（#123）のジャーニー。
 *
 * 見るのは #197 が確定した内容が画面に出ているか。記事の種別ラベルは契約（列挙子名）ではなく画面が
 * 決める文言のため、シナリオ側に置く。
 */

/** 種別ラベル。文言は画面の実装が持つ */
const ALBUM_TYPE_LABEL = '作品紹介';
const NOTE_TYPE_LABEL = 'ノート';

/** 参照先の作品への導線の見出し。文言は画面の実装が持つ */
const ALBUM_REFERENCE_HEADING = 'この記事の作品';

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

    /* 完全一致で見る。種別ラベルはフィクスチャのタイトルにも現れる語のため */
    await expect(page.getByText(ALBUM_TYPE_LABEL, { exact: true })).toBeVisible();
    /* 記事の見出しの中の日付を見る。参照先の作品も初出イベントの日付を持つため */
    await expect(page.locator('article header time[datetime]')).toBeVisible();
    await expect(
      page.getByRole('heading', { level: 2, name: albumArticle.body.heading }),
    ).toBeVisible();
    await expect(page.getByText(albumArticle.body.lead)).toBeVisible();
    await capture(page, '10-article-detail');
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

  test('作品を紹介する記事のリンクプレビューは、参照先の作品のもの', async ({ page }) => {
    await page.goto(await articlePathOf(albumArticle.title));

    /* 参照先の作品は外部音源を持つため、プレイヤーカードになる（#197） */
    await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute('content', 'player');

    const playerUrl = await page.locator('meta[name="twitter:player"]').getAttribute('content');
    expect(playerUrl).toContain(encodeURIComponent(showcase.audioUrl));
  });

  test('作品を参照しない記事には、作品への導線もリンクプレビューも出ない', async ({ page }) => {
    await page.goto(await articlePathOf(plainArticle.title));

    await expect(page.getByText(NOTE_TYPE_LABEL, { exact: true })).toBeVisible();
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

  test('下書きの詳細は開けない', async ({ page }) => {
    const response = await page.goto(await articlePathOf(draftArticle.title));

    /*
     * 未存在と非公開を区別せず、どちらも 404 にする（#197。下書きの存在を漏らさない）。静的出力の
     * ため下書きのページはそもそも組まれず、配信が 404 を返す。
     */
    expect(response?.status()).toBe(404);
  });
});
