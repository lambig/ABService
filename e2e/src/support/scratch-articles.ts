import {
  countArticles,
  deleteArticle,
  findArticlesByTitlePrefix,
  publishArticle,
  seedDraftArticle,
} from './admin-api.ts';
import { longTextOf } from './long-text.ts';

/**
 * シナリオの中だけで使う記事。
 *
 * <p>
 * SHARED-POPULATION: 公開サイトの一覧の検査は「母集団はシードしたものだけ」を前提にしている
 * （`articles.spec.ts`）。組み立ては実行の前に一度だけ走るため、検査の中で作った記事が残ると**次回の
 * 実行**の組み立てに混ざり、その前提を壊す。作った側で必ず片付ける。
 * </p>
 *
 * <p>
 * 片付けは控えではなくタイトルの接頭辞で拾う。控えを持つと、検査が途中で落ちた回の分が残る。
 * </p>
 */

/** 検査のためだけに作る記事のタイトルの接頭辞。シードした記事（`E2E 確認〜`）には当たらない */
export const SCRATCH_TITLE_PREFIX = 'E2E-SCRATCH 記事';

/**
 * 作った記事。
 *
 * <p>
 * 画面から指すためのタイトル、APIから操作するためのID、そして編集画面に**入っているはずの値**を持つ。
 * 期待値の出所を投入した値そのものにするため、シナリオ側へ文字列を書き写さない。
 * </p>
 */
export interface ScratchArticle {
  readonly articleId: string;
  readonly title: string;
  readonly introShort: string;
  readonly body: string;
}

const scratchTitle = (purpose: string): string =>
  `${SCRATCH_TITLE_PREFIX} ${purpose} ${String(Date.now())}`;

/**
 * 検査のためだけの記事を1つ作る（下書き）。
 *
 * @param purpose
 *            何のための記事かを表す短い語。タイトルに入る
 * @returns 作った記事のIDと、投入した値
 */
export const seedScratchArticle = async (purpose: string): Promise<ScratchArticle> => {
  const title = scratchTitle(purpose);
  const introShort = `E2E ${purpose}のショート紹介文。`;
  const body = `E2E ${purpose}の本文。`;

  const articleId = await seedDraftArticle({
    articleType: 'NOTE',
    title,
    body,
    bodyFormat: 'PLAIN_TEXT',
    introShort,
  });

  return { articleId, title, introShort, body };
};

/**
 * 検査のためだけの記事を1つ作り、公開する。
 *
 * @param purpose
 *            何のための記事かを表す短い語。タイトルに入る
 * @returns 作った記事のIDとタイトル
 */
export const seedPublishedScratchArticle = async (purpose: string): Promise<ScratchArticle> => {
  const article = await seedScratchArticle(purpose);
  await publishArticle(article.articleId);
  return article;
};

/**
 * 記事のタイトルに使える長さの上限。
 *
 * ドメインの `ArticleTitle` と列（`ArticleTableRecord` の `title`）が持つ値と揃える。ずれても検査は
 * 落ちない（作れる長さのままなので）が、そのときここは「起こりうる上限」を指していない。
 */
export const ARTICLE_TITLE_MAX_LENGTH = 500;

/**
 * 上限いっぱいのタイトルを持つ記事を1つ作る（下書き）。
 *
 * <p>
 * 長くするのはタイトルだけにする。管理の一覧が並べるのはタイトル・種別・公開日・状態・操作で、
 * 行の幅を動かしうるのはタイトルしかない。
 * </p>
 */
export const seedScratchArticleWithLongestTitle = async (): Promise<ScratchArticle> => {
  const title = longTextOf(`${scratchTitle('長いタイトル')} `, ARTICLE_TITLE_MAX_LENGTH);
  const introShort = 'E2E 長いタイトルの記事のショート紹介文。';
  const body = 'E2E 長いタイトルの記事の本文。';

  const articleId = await seedDraftArticle({
    articleType: 'NOTE',
    title,
    body,
    bodyFormat: 'PLAIN_TEXT',
    introShort,
  });

  return { articleId, title, introShort, body };
};

/**
 * 管理画面の一覧が1ページに並べる件数。
 *
 * <p>
 * 画面の実装（`frontend-admin` の `$lib/api/client.ts` の `PAGE_SIZE`）と揃える。ずれるとページ送りの
 * シナリオが落ちるため、揃っていないことに気付ける。
 * </p>
 */
export const ADMIN_ARTICLES_PER_PAGE = 50;

/**
 * 記事の総数が1ページに収まらない状態にする。
 *
 * <p>
 * 母集団はシードした記事と、前回までの実行が残したものの合計で、実行ごとに変わる。総数を数えてから
 * 足りない分だけ足す（固定の件数を作ると、母集団が増えたときに何件目を見ているのか分からなくなる）。
 * </p>
 */
export const seedArticlesBeyondFirstPage = async (): Promise<void> => {
  const total = await countArticles();
  const shortage = Math.max(ADMIN_ARTICLES_PER_PAGE + 1 - total, 0);

  /*
   * SEQUENTIAL-ORDER: タイトルに時刻を含めて一意にするため、同じミリ秒で並列に作ると重なる。
   * 通し番号を添えて1件ずつ送る。
   */
  for (const index of Array.from({ length: shortage }, (_unused, i) => i + 1)) {
    await seedDraftArticle({
      articleType: 'NOTE',
      title: `${SCRATCH_TITLE_PREFIX} 詰め物 ${String(index)} ${String(Date.now())}`,
    });
  }
};

/** 検査のためだけに作った記事を片付ける。作るシナリオを持つ spec の `afterEach` に置く */
export const deleteScratchArticles = async (): Promise<void> => {
  const leftovers = await findArticlesByTitlePrefix(SCRATCH_TITLE_PREFIX);

  /*
   * CONCURRENT-DELETE: 削除を並列に投げると、バックエンドが HR000069（reactive Session を開いた
   * スレッドと別のスレッドから使った）で 500 を返すことがある。片付けは検査の対象ではないため、
   * ここは1件ずつ送って通す。
   */
  for (const article of leftovers) {
    await deleteArticle(article.articleId);
  }
};
