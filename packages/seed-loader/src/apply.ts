import { readFile } from 'node:fs/promises';
import type { AdminApi } from 'abservice-admin-api';
import type { AlbumStep, ArticleStep, Plan, SiteContentStep } from './plan.ts';
import type { SeedArticle, SeedContent, SeedImage } from './seed.ts';

/**
 * 計画を管理APIへ流す。
 *
 * <p>
 * 順序は 文言 → 作品 → 記事。作品紹介の記事は参照先の作品を要し、公開は参照先が公開済みでなければ
 * 通らないため。1件ずつ直列に送り、どこまで進んだかを行で報告する。途中で落ちたら止まり、同じ
 * コマンドの再実行が入った分を飛ばして続きから進む（計画が「無いものだけを作る」ため）。
 * </p>
 */

/** 進行の1行を受け取る */
export type Reporter = (line: string) => void;

/** 画像の実体を読む。検査では差し替える */
export type ImageReader = (image: SeedImage) => Promise<Blob>;

/** 実行の結果の数 */
export interface Summary {
  readonly created: number;
  readonly published: number;
  readonly coverImagesAdded: number;
  readonly skipped: number;
}

const NOTHING: Summary = { created: 0, published: 0, coverImagesAdded: 0, skipped: 0 };

const readImageFromDisk: ImageReader = async (image) =>
  new Blob([await readFile(image.path)], { type: image.contentType });

const sum = (summaries: readonly Summary[]): Summary =>
  summaries.reduce(
    (total, item) => ({
      created: total.created + item.created,
      published: total.published + item.published,
      coverImagesAdded: total.coverImagesAdded + item.coverImagesAdded,
      skipped: total.skipped + item.skipped,
    }),
    NOTHING,
  );

const count = (flag: boolean): number => (flag ? 1 : 0);

/**
 * 1件ずつ順に流す。
 *
 * SEQUENTIAL-ORDER: 記事は参照先の作品を要し、公開は参照先が公開済みでなければ通らない。並列に送ると
 * どこまで入ったのかも追えなくなるため、前の1件を待ってから次を送る。
 */
const sequentially = <T>(
  items: readonly T[],
  run: (item: T) => Promise<Summary>,
): Promise<readonly Summary[]> =>
  items.reduce<Promise<readonly Summary[]>>(
    async (done, item) => [...(await done), await run(item)],
    Promise.resolve([]),
  );

const seedOf = <T>(items: readonly T[], matches: (item: T) => boolean, label: string): T =>
  items.find(matches) ??
  (() => {
    throw new Error(`計画にある ${label} が投入内容にありません`);
  })();

const albumIdOf = async (api: AdminApi, catalogNumber: string): Promise<string> =>
  (await api.findAlbumByCatalogNumber(catalogNumber))?.albumId ??
  (() => {
    throw new Error(`作品 ${catalogNumber} が投入先にありません`);
  })();

const articleIdOf = async (api: AdminApi, title: string): Promise<string> =>
  (await api.findArticleByTitle(title))?.articleId ??
  (() => {
    throw new Error(`記事「${title}」が投入先にありません`);
  })();

/** 落ちた段を示して止める。再実行で続きから進めることも、ここで伝える */
const stopped = (label: string, cause: unknown): Error =>
  new Error(
    [
      `${label} で止まりました。`,
      '原因を直してから同じコマンドを再実行すると、入った分は飛ばして続きから進みます。',
      cause instanceof Error ? cause.message : String(cause),
    ].join('\n'),
    { cause },
  );

const guarded = async <T>(label: string, run: () => Promise<T>): Promise<T> => {
  try {
    return await run();
  } catch (cause) {
    throw stopped(label, cause);
  }
};

const when = <T>(flag: boolean, run: () => Promise<T>): Promise<T | undefined> =>
  flag ? run() : Promise.resolve(undefined);

const describeDone = (done: readonly string[]): string =>
  done.length === 0 ? '飛ばした' : done.join('・');

const applySiteContent = async (
  api: AdminApi,
  seed: SeedContent,
  step: SiteContentStep,
  report: Reporter,
): Promise<Summary> => {
  const content = seedOf(seed.siteContents, (item) => item.key === step.key, `文言 ${step.key}`);
  await when(step.create, () =>
    guarded(`文言 ${step.key} の登録`, () => api.upsertSiteContent(content)),
  );
  report(`文言 ${step.key}: ${step.create ? '登録した' : '飛ばした'}`);
  return { ...NOTHING, created: count(step.create), skipped: step.create ? 0 : 1 };
};

const imageBody = async (image: SeedImage, readImage: ImageReader) => ({
  contentType: image.contentType,
  body: await readImage(image),
});

const applyAlbum = async (
  api: AdminApi,
  seed: SeedContent,
  step: AlbumStep,
  readImage: ImageReader,
  report: Reporter,
): Promise<Summary> => {
  const album = seedOf(
    seed.albums,
    (item) => item.catalogNumber === step.catalogNumber,
    `作品 ${step.catalogNumber}`,
  );
  const label = `作品 ${step.catalogNumber}`;
  const cover = album.coverImage;

  const albumId = step.create
    ? await guarded(`${label} の作成`, async () =>
        api.seedDraftAlbum({
          ...album.input,
          ...(cover === undefined ? {} : { coverImage: await imageBody(cover, readImage) }),
        }),
      )
    : await albumIdOf(api, step.catalogNumber);

  await when(step.addCoverImage && cover !== undefined, () =>
    guarded(`${label} への画像の設定`, async () =>
      api.setAlbumCoverImage(
        albumId,
        await api.seedAsset(
          await imageBody(
            cover ??
              (() => {
                throw new Error(`${label} の画像がありません`);
              })(),
            readImage,
          ),
        ),
      ),
    ),
  );

  await when(step.publish, () => guarded(`${label} の公開`, () => api.publishAlbum(albumId)));

  const done = [
    ...(step.create ? ['作成した'] : []),
    ...(step.addCoverImage ? ['画像を付けた'] : []),
    ...(step.publish ? ['公開した'] : []),
  ];
  report(`${label}: ${describeDone(done)}`);
  return {
    created: count(step.create),
    published: count(step.publish),
    coverImagesAdded: count(step.addCoverImage),
    skipped: count(done.length === 0),
  };
};

const articleInput = async (
  api: AdminApi,
  article: SeedArticle,
): Promise<Parameters<AdminApi['seedDraftArticle']>[0]> =>
  article.albumCatalogNumber === undefined
    ? article.input
    : { ...article.input, albumId: await albumIdOf(api, article.albumCatalogNumber) };

const applyArticle = async (
  api: AdminApi,
  seed: SeedContent,
  step: ArticleStep,
  report: Reporter,
): Promise<Summary> => {
  const article = seedOf(
    seed.articles,
    (item) => item.title === step.title,
    `記事「${step.title}」`,
  );
  const label = `記事「${step.title}」`;

  const articleId = step.create
    ? await guarded(`${label} の作成`, async () =>
        api.seedDraftArticle(await articleInput(api, article)),
      )
    : await articleIdOf(api, step.title);

  await when(step.publish, () => guarded(`${label} の公開`, () => api.publishArticle(articleId)));

  const done = [...(step.create ? ['作成した'] : []), ...(step.publish ? ['公開した'] : [])];
  report(`${label}: ${describeDone(done)}`);
  return {
    ...NOTHING,
    created: count(step.create),
    published: count(step.publish),
    skipped: count(done.length === 0),
  };
};

const executable = (plan: Plan): Plan =>
  plan.problems.length === 0
    ? plan
    : (() => {
        throw new Error(`計画に問題があるため実行しません:\n${plan.problems.join('\n')}`);
      })();

/**
 * 計画を実行する。
 *
 * @param plan
 *            {@link planSeed} が立てた計画。`problems` があれば実行せずに落とす
 * @param seed
 *            投入内容
 * @param api
 *            投入先に結び付いた管理APIクライアント
 * @param report
 *            進行の行を受け取る
 * @param readImage
 *            画像の実体を読む（既定はファイルから）
 */
export const applyPlan = async (
  plan: Plan,
  seed: SeedContent,
  api: AdminApi,
  report: Reporter,
  readImage: ImageReader = readImageFromDisk,
): Promise<Summary> => {
  const checked = executable(plan);
  const siteContents = await sequentially(checked.siteContents, (step) =>
    applySiteContent(api, seed, step, report),
  );
  const albums = await sequentially(checked.albums, (step) =>
    applyAlbum(api, seed, step, readImage, report),
  );
  const articles = await sequentially(checked.articles, (step) =>
    applyArticle(api, seed, step, report),
  );
  return sum([...siteContents, ...albums, ...articles]);
};
