import { setTimeout as delay } from 'node:timers/promises';

import type { Locator, Page } from '@playwright/test';

import { albumArticle, draftArticle } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture, clickWithEvidence } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import {
  deleteScratchArticles,
  seedArticlesBeyondFirstPage,
  seedPublishedScratchArticle,
  seedScratchArticle,
} from '../support/scratch-articles.ts';

/**
 * 管理画面の記事一覧（#309）のジャーニー。
 *
 * 作品の一覧と同じく、画面の中身はブラウザが管理APIから引く（下書きを含むため、組み立ての時点の
 * 内容を配れない）。作品と違うのは、応答が返したページ情報を保って1ページを超える記事も辿ること。
 *
 * 文言は画面の実装が持つため、シナリオ側に置く。
 */

/** 記事一覧の所在 */
const ARTICLE_LIST_URL = `${stack.adminBaseUrl}/articles`;

/** 鍵の入力欄のラベルと、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 公開状態のラベル。行の中で完全一致で指す（`公開する` の一部として当たらないようにする） */
const DRAFT_LABEL = '下書き';
const PUBLISHED_LABEL = '公開';

/** 一覧に置く操作 */
const PUBLISH_LABEL = '公開する';
const UNPUBLISH_LABEL = '非公開にする';
const DELETE_LABEL = '削除する';

/** 確認の対話 */
const DELETE_DIALOG_TITLE = 'この記事を削除しますか';
const CANCEL_LABEL = 'やめる';

/** ページ送りの操作 */
const NEXT_PAGE_LABEL = '次のページ';
const PREVIOUS_PAGE_LABEL = '前のページ';

/** 1ページ目に出る範囲。1ページの件数は画面が決める（`ADMIN_ARTICLES_PER_PAGE` と揃えている） */
const FIRST_PAGE_RANGE = '1–50 件目';

/** 公開の要求だけを遅らせる経路。送信中の一覧の様子を見るために分ける */
const PUBLISH_API = `${stack.backendBaseUrl}/api/v1/articles/*/publish`;

/** 送信中を観測するための待ち時間 */
const SLOW_PUBLISH_MS = 2_000;

/** 鍵を入れて一覧が出た状態にする */
const openArticles = async (page: Page): Promise<void> => {
  await page.goto(ARTICLE_LIST_URL);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
  await expect(page.getByRole('table')).toBeVisible();
};

/** タイトルで一覧の行を指す。操作ボタンは行ごとに並ぶため、行を経由して押す */
const rowOf = (page: Page, title: string): Locator =>
  page.getByRole('row').filter({ hasText: title });

/* 検査の中で作った記事を残さない。残ると次回の組み立てに混ざり、公開サイトの母集団の前提を壊す */
test.afterEach(deleteScratchArticles);

test.describe('管理画面の記事一覧', () => {
  test('鍵を入れるまで記事を出さない', async ({ page }) => {
    await page.goto(ARTICLE_LIST_URL);

    await expect(page.getByLabel(API_KEY_LABEL)).toBeVisible();
    await expect(page.getByText(albumArticle.title)).toHaveCount(0);
  });

  test('鍵を入れると、下書きを含む記事が種別とともに並ぶ', async ({ page }) => {
    await page.goto(ARTICLE_LIST_URL);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await expect(rowOf(page, albumArticle.title)).toBeVisible();

    /*
     * 下書きは公開サイトのどこにも出ない（#197）。管理画面はそれを状態とともに並べる。ここが
     * 公開向けと管理向けで応答が違うことの確認になる。
     */
    await expect(
      rowOf(page, draftArticle.title).getByText(DRAFT_LABEL, { exact: true }),
    ).toBeVisible();
    await capture(page, '40-admin-articles');
  });

  test('下書きは公開でき、公開すると状態が変わる', async ({ page }) => {
    const article = await seedScratchArticle('公開');

    await openArticles(page);
    await expect(rowOf(page, article.title).getByText(DRAFT_LABEL, { exact: true })).toBeVisible();

    await rowOf(page, article.title).getByRole('button', { name: PUBLISH_LABEL }).click();

    await expect(
      rowOf(page, article.title).getByText(PUBLISHED_LABEL, { exact: true }),
    ).toBeVisible();
    await expect(
      rowOf(page, article.title).getByRole('button', { name: UNPUBLISH_LABEL }),
    ).toBeVisible();
  });

  test('公開中の記事は下書きへ戻せる', async ({ page }) => {
    const article = await seedPublishedScratchArticle('非公開');

    await openArticles(page);
    await rowOf(page, article.title).getByRole('button', { name: UNPUBLISH_LABEL }).click();

    await expect(rowOf(page, article.title).getByText(DRAFT_LABEL, { exact: true })).toBeVisible();
  });

  test('削除は確認を経て、一覧から消える', async ({ page }) => {
    const article = await seedScratchArticle('削除');

    await openArticles(page);
    await rowOf(page, article.title).getByRole('button', { name: DELETE_LABEL }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(DELETE_DIALOG_TITLE);
    await expect(dialog).toContainText(article.title);

    /* 確定すると一覧から消える。消えたことを一覧の読み直しで見る（画面側で行を隠すのではない） */
    await clickWithEvidence(
      page,
      dialog.getByRole('button', { name: DELETE_LABEL }),
      '41-admin-article-delete-confirm',
    );

    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(rowOf(page, article.title)).toHaveCount(0);
  });

  test('削除をやめれば、記事は残る', async ({ page }) => {
    const article = await seedScratchArticle('削除取り消し');

    await openArticles(page);
    await rowOf(page, article.title).getByRole('button', { name: DELETE_LABEL }).click();

    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: CANCEL_LABEL }).click();

    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(rowOf(page, article.title)).toBeVisible();
  });

  test('送信中は一覧の操作を受け付けない', async ({ page }) => {
    const article = await seedScratchArticle('多重送信');

    await openArticles(page);

    /*
     * 応答を遅らせて、送信中の一覧を観測できるようにする。塞がっていないと、同じ操作を重ねられて
     * 先に投げた要求の結果が後から返り、後の結果を上書きし得る。
     */
    await page.route(PUBLISH_API, async (route) => {
      await delay(SLOW_PUBLISH_MS);
      await route.continue();
    });
    await rowOf(page, article.title).getByRole('button', { name: PUBLISH_LABEL }).click();

    await expect(
      rowOf(page, article.title).getByRole('button', { name: PUBLISH_LABEL }),
    ).toBeDisabled();
    await expect(
      rowOf(page, article.title).getByRole('button', { name: DELETE_LABEL }),
    ).toBeDisabled();

    await page.unroute(PUBLISH_API);
    await expect(
      rowOf(page, article.title).getByRole('button', { name: UNPUBLISH_LABEL }),
    ).toBeVisible();
  });

  test('1ページに収まらない記事は、ページを送って辿れる', async ({ page }) => {
    await seedArticlesBeyondFirstPage();

    await openArticles(page);
    await expect(page.getByText(FIRST_PAGE_RANGE)).toBeVisible();
    await expect(page.getByRole('button', { name: PREVIOUS_PAGE_LABEL })).toBeDisabled();

    await clickWithEvidence(
      page,
      page.getByRole('button', { name: NEXT_PAGE_LABEL }),
      '42-admin-articles-next-page',
    );

    /* 2ページ目は51件目から始まる。総件数は実行ごとに変わるため、始まりだけを見る */
    await expect(page.getByText(/^51–/u)).toBeVisible();
    await expect(page.getByRole('button', { name: PREVIOUS_PAGE_LABEL })).toBeEnabled();

    await page.getByRole('button', { name: PREVIOUS_PAGE_LABEL }).click();
    await expect(page.getByText(FIRST_PAGE_RANGE)).toBeVisible();
  });
});
