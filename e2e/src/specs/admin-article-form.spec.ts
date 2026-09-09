import { setTimeout as delay } from 'node:timers/promises';

import type { Locator, Page } from '@playwright/test';

import { renameArticleOutsideTheScreen } from '../support/admin-api.ts';
import { albumArticle } from '../support/build-fixtures.ts';
import { stack } from '../support/config.ts';
import { capture, clickWithEvidence, focusOn } from '../support/evidence.ts';
import { expect, test } from '../support/fixtures.ts';
import {
  SCRATCH_TITLE_PREFIX,
  deleteScratchArticles,
  seedScratchArticle,
} from '../support/scratch-articles.ts';

/**
 * 管理画面の記事の新規作成・編集（#309）のジャーニー。
 *
 * 作品の編集と違い、保存しても画面を離れない。保存のたびに保存後の世代を持ち直して、そのまま続けて
 * 直せることを見る。
 *
 * 文言は画面の実装が持つため、シナリオ側に置く。
 */

/** 画面の所在 */
const ARTICLE_LIST_URL = `${stack.adminBaseUrl}/articles`;
const NEW_ARTICLE_URL = `${stack.adminBaseUrl}/articles/new`;
const EDIT_ARTICLE_URL = `${stack.adminBaseUrl}/articles/edit`;

/** 鍵の入力欄のラベルと、鍵を送る操作 */
const API_KEY_LABEL = '管理APIの鍵';
const OPEN_LABEL = '開く';

/** 受け付けられない鍵 */
const WRONG_API_KEY = 'e2e-wrong-key';

/** 入力欄のラベル。`本文` は `本文の形式` の一部に当たるため、完全一致で指す */
const TYPE_LABEL = '種別';
const TITLE_LABEL = 'タイトル';
const INTRO_SHORT_LABEL = 'ショート紹介文';
const BODY_LABEL = '本文';
const BODY_FORMAT_LABEL = '本文の形式';

/** 保存の操作と、その結果 */
const CREATE_LABEL = '作成する';
const SAVE_LABEL = '保存する';
const SAVED_NOTICE = '保存しました。';

/** 一覧に置く操作 */
const EDIT_LABEL = '編集する';
const NEW_ARTICLE_LABEL = '記事を追加する';

/** 鍵を入れ直しても入力を抱えていることを伝える文言 */
const PENDING_NOTICE = '入力した内容は保持しています。鍵を入れ直すと、続けて保存できます。';

/** 競合したときの見出しと復帰の操作 */
const CONFLICT_HEADING = '編集を始めた後に、別の操作がこの記事を保存しています';
const RELOAD_LABEL = '最新を読み込む';

/** タグの区画。付け外しは本文の保存と別の経路のため、操作も文言も分かれている */
const TAG_SELECT_LABEL = '付けるタグ';
const TAG_ADD_LABEL = '付ける';
const TAG_REMOVE_LABEL = '外す';
const NO_TAGS_TEXT = 'タグは付いていません。';
const TAGS_NEED_ARTICLE_TEXT = '記事を作成すると、タグを付けられます。';

/** 付いているタグの一覧 */
const attachedTagsOf = (page: Page): Locator => page.locator('[data-tags="attached"]');

/** 本文のプレビュー。形式によって描き方が変わるため、区画を分けて指す */
const markdownPreviewOf = (page: Page): Locator => page.locator('[data-preview="markdown"]');
const plainPreviewOf = (page: Page): Locator => page.locator('[data-preview="plain"]');

/** 記法を確かめるための本文。投入した値をそのまま期待値に使う */
const MARKDOWN_BODY = {
  heading: 'プレビューの見出し',
  bullets: ['ひとつめ', 'ふたつめ'],
  emphasis: '強調',
} as const;

const markdownBodyText = [
  `## ${MARKDOWN_BODY.heading}`,
  '',
  `- ${MARKDOWN_BODY.bullets[0]}`,
  `- ${MARKDOWN_BODY.bullets[1]}`,
  '',
  `**${MARKDOWN_BODY.emphasis}**`,
  '',
].join('\n');

/** 記事そのものへの要求。保存を遅らせたり塞いだりするために使う */
const ARTICLE_COMMAND_API = `${stack.backendBaseUrl}/api/v1/articles/*`;

/** 管理向けの記事詳細。読み込みだけを断らせるために、保存の経路と分ける */
const ARTICLE_DETAIL_API = `${stack.backendBaseUrl}/api/v1/admin/articles/*`;

/** 保存中を観測するための待ち時間 */
const SLOW_SAVE_MS = 2_000;

/** 欄を位置で指す。エラーは欄の中に出る */
const fieldOf = (page: Page, path: string): Locator => page.locator(`[data-field="${path}"]`);

/** タイトルで一覧の行を指す */
const rowOf = (page: Page, title: string): Locator =>
  page.getByRole('row').filter({ hasText: title });

/**
 * 作成の後に経路へ入る対象。
 *
 * 作成できた時点で、画面はその記事の編集を指す。画面の外からその記事を動かすシナリオが対象を知る
 * 手立ては経路しかない（作成の応答はシナリオから見えない）。
 */
const createdArticleId = (page: Page): string => {
  const articleId = new URL(page.url()).searchParams.get('articleId');

  return (
    articleId ??
    (() => {
      throw new Error(`作成した後の経路に articleId がありません: ${page.url()}`);
    })()
  );
};

/** 鍵を入れて、その画面が開いた状態にする */
const openWithKey = async (page: Page, url: string): Promise<void> => {
  await page.goto(url);
  await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
  await page.getByRole('button', { name: OPEN_LABEL }).click();
};

/* 検査の中で作った記事を残さない。残ると次回の組み立てに混ざり、公開サイトの母集団の前提を壊す */
test.afterEach(deleteScratchArticles);

test.describe('管理画面の記事の編集', () => {
  test('一覧から編集を開くと、登録されている値が入っている', async ({ page }) => {
    const article = await seedScratchArticle('編集');

    await openWithKey(page, ARTICLE_LIST_URL);
    await clickWithEvidence(
      page,
      rowOf(page, article.title).getByRole('link', { name: EDIT_LABEL }),
      '44-admin-article-edit-open',
    );

    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(article.title);
    await expect(page.getByLabel(INTRO_SHORT_LABEL)).toHaveValue(article.introShort);
    await expect(page.getByLabel(BODY_LABEL, { exact: true })).toHaveValue(article.body);
    await expect(page.getByLabel(TYPE_LABEL)).toHaveValue('NOTE');
    await expect(page.getByLabel(BODY_FORMAT_LABEL)).toHaveValue('PLAIN_TEXT');
    await capture(page, '45-admin-article-edit-loaded');
  });

  test('検証エラーは、応答が返した位置のとおりに各欄へ出る', async ({ page }) => {
    const article = await seedScratchArticle('検証エラー');

    await page.goto(`${EDIT_ARTICLE_URL}?articleId=${article.articleId}`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    /* 必須の判定はバックエンドが持つ。画面は同じ規則を持たず、返った位置へ出すだけ */
    await page.getByLabel(TITLE_LABEL).fill('');
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(fieldOf(page, 'title').getByRole('alert')).toBeVisible();
    await capture(page, '46-admin-article-field-errors');

    /* 入力は失わない。直す先が入力にあるため、消さずに残す */
    await expect(page.getByLabel(INTRO_SHORT_LABEL)).toHaveValue(article.introShort);
  });

  test('保存しても画面に留まり、続けて直せる', async ({ page }) => {
    const article = await seedScratchArticle('保存して留まる');
    const renamed = `${article.title} 改題`;

    await page.goto(`${EDIT_ARTICLE_URL}?articleId=${article.articleId}`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await page.getByLabel(TITLE_LABEL).fill(renamed);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: SAVE_LABEL }),
      '47-admin-article-save',
    );

    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(renamed);
    await capture(page, '48-admin-article-saved');

    /*
     * 続けてもう一度保存できる。保存後の世代を持ち直していなければ、2回目は競合として断られる（#287）。
     */
    await page.getByLabel(BODY_LABEL, { exact: true }).fill('二度目の本文。');
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();

    /* 保存されたことは一覧で見る（画面が持っている値ではなく、引き直した内容で確かめる） */
    await page.goto(ARTICLE_LIST_URL);
    await expect(rowOf(page, renamed)).toBeVisible();
  });

  test('保存に到達できないときは、入力を保ったまま理由を出す', async ({ page }) => {
    const article = await seedScratchArticle('保存できない');

    await page.goto(`${EDIT_ARTICLE_URL}?articleId=${article.articleId}`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await page.getByLabel(TITLE_LABEL).fill(`${article.title} 未達`);

    /* 応答を差し替えるのではなくネットワークの側で塞ぐ（#164 の「APIのモックはしない」に沿う） */
    await page.route(ARTICLE_COMMAND_API, (route) => route.abort());
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(`${article.title} 未達`);
    await capture(page, '49-admin-article-unreachable');

    await page.unroute(ARTICLE_COMMAND_API);
  });

  test('保存中は入力も塞ぐ（送信後の変更が黙って消えない）', async ({ page }) => {
    const article = await seedScratchArticle('保存中');

    await page.goto(`${EDIT_ARTICLE_URL}?articleId=${article.articleId}`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await page.route(ARTICLE_COMMAND_API, async (route) => {
      await delay(SLOW_SAVE_MS);
      await route.continue();
    });
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(page.getByLabel(TITLE_LABEL)).toBeDisabled();
    await capture(page, '50-admin-article-saving');

    await page.unroute(ARTICLE_COMMAND_API);
    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();
  });

  test('編集中に別の操作が保存していたら、入力を保ったまま競合を伝える', async ({ page }) => {
    const article = await seedScratchArticle('競合');

    await page.goto(`${EDIT_ARTICLE_URL}?articleId=${article.articleId}`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(article.title);

    /* 画面を開いたまま、別の経路が保存する。これで画面が持つ世代は古くなる */
    await renameArticleOutsideTheScreen(article.articleId, `${article.title} 別の保存`);

    await page.getByLabel(TITLE_LABEL).fill(`${article.title} こちらの編集`);
    await page.getByRole('button', { name: SAVE_LABEL }).click();

    await expect(page.getByText(CONFLICT_HEADING)).toBeVisible();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(`${article.title} こちらの編集`);
    await capture(page, '51-admin-article-conflicted');

    /* 読み直すと、保存されている内容へ置き換わる（古い値を自動で再送しない） */
    await page.getByRole('button', { name: RELOAD_LABEL }).click();
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(`${article.title} 別の保存`);
  });

  test('対象を指定せずに編集を開くと、対象が無いと言う', async ({ page }) => {
    await openWithKey(page, EDIT_ARTICLE_URL);

    await expect(page.getByRole('alert')).toContainText('編集する記事が指定されていません。');
  });
});

test.describe('管理画面の記事の追加', () => {
  test('必須を入れて作成すると、そのまま続けて編集できる', async ({ page }) => {
    const title = `${SCRATCH_TITLE_PREFIX} 画面から追加 ${String(Date.now())}`;

    await openWithKey(page, ARTICLE_LIST_URL);
    await clickWithEvidence(
      page,
      page.getByRole('link', { name: NEW_ARTICLE_LABEL }),
      '52-admin-article-new-open',
    );

    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByLabel(INTRO_SHORT_LABEL).fill('画面から追加した記事のショート紹介文。');
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: CREATE_LABEL }),
      '53-admin-article-create',
    );

    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();

    /* 作成の後は更新になる。文言も、経路もその記事のものへ変わる */
    await expect(page.getByRole('button', { name: SAVE_LABEL })).toBeVisible();
    await expect(page).toHaveURL(/articleId=/u);
    await capture(page, '54-admin-article-created');

    /* 続けて直して保存できる（作成の後に世代を持ち直している） */
    await page.getByLabel(BODY_LABEL, { exact: true }).fill('作成の直後に書いた本文。');
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();

    await page.goto(ARTICLE_LIST_URL);
    await expect(rowOf(page, title)).toBeVisible();
  });

  test('鍵が断られても入力は残り、入れ直せば続けて作成できる', async ({ page }) => {
    const title = `${SCRATCH_TITLE_PREFIX} 鍵を入れ直して追加 ${String(Date.now())}`;

    /*
     * 新規作成は鍵の入力時に管理APIを呼ばない（読み込むものが無い）。したがって鍵が正しいと分かるのは
     * 最初の保存のときで、そこで入力を捨てると全項目を書き直させることになる。
     */
    await page.goto(NEW_ARTICLE_URL);
    await page.getByLabel(API_KEY_LABEL).fill(WRONG_API_KEY);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByLabel(BODY_LABEL, { exact: true }).fill('鍵を入れ直しても残る本文。');
    await page.getByRole('button', { name: CREATE_LABEL }).click();

    await expect(page.getByLabel(API_KEY_LABEL)).toBeVisible();
    await expect(page.getByText(PENDING_NOTICE)).toBeVisible();

    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    /* 書いた内容がそのまま戻る（読み直しも初期化もしない） */
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(title);
    await expect(page.getByLabel(BODY_LABEL, { exact: true })).toHaveValue(
      '鍵を入れ直しても残る本文。',
    );

    await page.getByRole('button', { name: CREATE_LABEL }).click();
    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();
  });

  test('必須を入れずに作成すると、その欄にエラーが出る', async ({ page }) => {
    await openWithKey(page, NEW_ARTICLE_URL);

    await page.getByRole('button', { name: CREATE_LABEL }).click();

    await expect(fieldOf(page, 'title').getByRole('alert')).toBeVisible();
  });

  test('作成した後に競合しても、読み直す先は作った記事である', async ({ page }) => {
    const title = `${SCRATCH_TITLE_PREFIX} 作成後の競合 ${String(Date.now())}`;

    await openWithKey(page, NEW_ARTICLE_URL);
    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByRole('button', { name: CREATE_LABEL }).click();
    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();

    /* 作成の後も同じ画面に留まっている。この状態で、別の経路がその記事を保存する */
    await renameArticleOutsideTheScreen(createdArticleId(page), `${title} 別の保存`);

    await page.getByLabel(TITLE_LABEL).fill(`${title} こちらの編集`);
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByText(CONFLICT_HEADING)).toBeVisible();

    await clickWithEvidence(
      page,
      page.getByRole('button', { name: RELOAD_LABEL }),
      '55-admin-article-created-conflict-reload',
    );

    /*
     * 読み直す先は作った記事で、新規作成の画面ではない。空の新規作成へ戻すと、そのまま保存した人が
     * 同じ内容の記事をもう1件作ることになる。
     */
    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(`${title} 別の保存`);
    await expect(page.getByRole('button', { name: SAVE_LABEL })).toBeVisible();
  });

  test('読み直しが鍵で止まっても、入れ直せば作った記事が読み込まれる', async ({ page }) => {
    const title = `${SCRATCH_TITLE_PREFIX} 読み直しの再認証 ${String(Date.now())}`;

    await openWithKey(page, NEW_ARTICLE_URL);
    await page.getByLabel(TITLE_LABEL).fill(title);
    await page.getByRole('button', { name: CREATE_LABEL }).click();
    await expect(page.getByText(SAVED_NOTICE)).toBeVisible();

    await renameArticleOutsideTheScreen(createdArticleId(page), `${title} 別の保存`);

    await page.getByLabel(TITLE_LABEL).fill(`${title} こちらの編集`);
    await page.getByRole('button', { name: SAVE_LABEL }).click();
    await expect(page.getByText(CONFLICT_HEADING)).toBeVisible();

    /*
     * 読み直しだけを断らせる。鍵だけを受け付けられないものへ差し替えて送り、応答はバックエンドに
     * 返させる（#164 の「APIのモックはしない」）。
     */
    await page.route(ARTICLE_DETAIL_API, (route) =>
      route.continue({
        headers: { ...route.request().headers(), authorization: `Bearer ${WRONG_API_KEY}` },
      }),
    );
    await page.getByRole('button', { name: RELOAD_LABEL }).click();

    await expect(page.getByLabel(API_KEY_LABEL)).toBeVisible();
    await page.unroute(ARTICLE_DETAIL_API);

    /*
     * 断られたのは読み込みで、画面の開き方ではない。鍵を入れ直したら、続きはその記事の読み直しになる
     * （新規作成として開いた画面でも、空の入力へ戻さない）。
     */
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: OPEN_LABEL }),
      '56-admin-article-reload-reauth',
    );

    await expect(page.getByLabel(TITLE_LABEL)).toHaveValue(`${title} 別の保存`);
    await expect(page.getByRole('button', { name: SAVE_LABEL })).toBeVisible();
  });
});

/**
 * 本文のプレビュー（#309 / DECISIONS 24）。
 *
 * 描画は公開サイトと同じ共有の関数を通る。ここで見ているのは、その関数が管理画面でも同じ結果を出すこと
 * と、形式によって解釈を変えないことである。保存はしないため、記事は作られない。
 */
/**
 * 記事のタグ（#309 / #120）。
 *
 * 付け外しは本文の保存とは別の経路で、押した時点で反映される。既にある名前から選ぶだけで、同名かどうかの
 * 判定はバックエンドが持つ（DECISIONS 23）。
 */
test.describe('管理画面の記事のタグ', () => {
  test('既存のタグを選んで付け、外せる', async ({ page }) => {
    const article = await seedScratchArticle('タグ');

    await page.goto(`${EDIT_ARTICLE_URL}?articleId=${article.articleId}`);
    await page.getByLabel(API_KEY_LABEL).fill(stack.adminApiKey);
    await page.getByRole('button', { name: OPEN_LABEL }).click();

    await expect(page.getByText(NO_TAGS_TEXT)).toBeVisible();

    /* 候補はシードした記事が持つタグ。名前で選び、画面は同名の判定をしない */
    await page.getByLabel(TAG_SELECT_LABEL).selectOption(albumArticle.tags[0]);
    await clickWithEvidence(
      page,
      page.getByRole('button', { name: TAG_ADD_LABEL }),
      '60-admin-article-tag-add',
    );

    await expect(attachedTagsOf(page)).toContainText(albumArticle.tags[0]);
    await capture(page, '61-admin-article-tag-attached');

    /* 保存を挟まずに反映されている。読み直しても付いたまま */
    await page.reload();
    await expect(attachedTagsOf(page)).toContainText(albumArticle.tags[0]);

    await attachedTagsOf(page).getByRole('button', { name: TAG_REMOVE_LABEL }).click();
    await expect(page.getByText(NO_TAGS_TEXT)).toBeVisible();
  });

  test('まだ作られていない記事にはタグを付けられない', async ({ page }) => {
    await openWithKey(page, NEW_ARTICLE_URL);

    /* 付ける先が無い。作成の前に選ばせると、押せない操作を出すことになる */
    await expect(page.getByText(TAGS_NEED_ARTICLE_TEXT)).toBeVisible();
    await expect(page.getByLabel(TAG_SELECT_LABEL)).toHaveCount(0);
  });
});

test.describe('管理画面の本文のプレビュー', () => {
  test('Markdown は記法として描かれる', async ({ page }) => {
    await openWithKey(page, NEW_ARTICLE_URL);

    await page.getByLabel(BODY_LABEL, { exact: true }).fill(markdownBodyText);
    await page.getByLabel(BODY_FORMAT_LABEL).selectOption('MARKDOWN');

    const preview = markdownPreviewOf(page);
    await expect(preview.getByRole('heading', { name: MARKDOWN_BODY.heading })).toBeVisible();
    await expect(preview.getByRole('listitem')).toHaveText([...MARKDOWN_BODY.bullets]);
    await expect(preview.locator('strong')).toHaveText(MARKDOWN_BODY.emphasis);

    /* 証跡は、入力と描画結果が同じ画面に並んでいることが分かる位置で撮る */
    await focusOn(preview);
    await capture(page, '57-admin-article-preview');
  });

  test('プレーンテキストは記法として解釈されない', async ({ page }) => {
    await openWithKey(page, NEW_ARTICLE_URL);

    /* 新規作成の初期値はプレーンテキスト。形式を変えずに、記法の見た目を含む本文を入れる */
    await page.getByLabel(BODY_LABEL, { exact: true }).fill(markdownBodyText);

    await expect(markdownPreviewOf(page)).toHaveCount(0);
    await expect(plainPreviewOf(page)).toContainText(`**${MARKDOWN_BODY.emphasis}**`);
    await expect(plainPreviewOf(page).locator('strong')).toHaveCount(0);
  });

  test('生HTMLは描かれない', async ({ page }) => {
    await openWithKey(page, NEW_ARTICLE_URL);

    /*
     * 生HTMLはパースしない（DECISIONS 24）。管理APIキーをブラウザに置ける前提が、描画側フィルタの
     * 網羅性ではなく入口を塞いでいることに依っているため、ここが崩れていないことを見る。
     */
    await page
      .getByLabel(BODY_LABEL, { exact: true })
      .fill(
        ['<script>alert(1)</script>', '', '<b>太字にはならない</b>', '', '本文', ''].join('\n'),
      );
    await page.getByLabel(BODY_FORMAT_LABEL).selectOption('MARKDOWN');

    const preview = markdownPreviewOf(page);
    await expect(preview.locator('script')).toHaveCount(0);
    await expect(preview.locator('b')).toHaveCount(0);
    await expect(preview).toContainText('本文');
  });
});
