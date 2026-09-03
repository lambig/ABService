import {
  deleteArticle,
  findAlbumByCatalogNumber,
  findArticleByTitle,
  publishAlbum,
  publishArticle,
  seedDraftAlbum,
  seedDraftArticle,
  unpublishAlbum,
  upsertSiteContent,
} from './admin-api.ts';
import type { AlbumSeed, ArticleSeed } from './admin-api.ts';

/**
 * 公開サイトを組む前に入れておくデータ。
 *
 * <p>
 * 公開サイトは静的出力で、ビルド時の内容がそのまま HTML になる（DECISIONS 24）。シナリオの中で投入した
 * データは**その回のビルドには入らない**ため、画面に現れてほしいものはここへ置く。
 * </p>
 *
 * <p>
 * 投入は冪等にする。ローカルでは同じ DB へ繰り返し実行するため、毎回足すと同じものが並んで証跡が
 * 読みにくくなる。作品はカタログナンバーで存在を見て無いときだけ作り、公開状態だけを毎回揃える
 * （削除が塞がっている。#251）。記事は削除できるため、あれば消してから作り直し、内容まで揃える。
 * </p>
 *
 * <p>
 * 画面に出る文言はここを唯一の出所にし、シナリオは投入した値そのものを期待値に使う。テストの中に
 * 文言を書き写すと、どちらが正なのか読み手に判断できなくなる。
 * </p>
 */

/** 外部音源とチューンを持つ作品。一覧から詳細までのジャーニーで使う */
export const showcase = {
  catalogNumber: 'E2E-0001',
  title: 'E2E 確認アルバム',
  artistDisplayName: 'E2E 確認アーティスト',
  releaseDate: '2026-08-15',
  /** 画面に出る形。投入値と並べて置き、整形の結果をシナリオから読めるようにする */
  releaseDateText: '2026年8月15日',
  trackTitle: 'E2E 確認トラック',
  tuneTitle: 'E2E 確認チューン',
  composerCredit: 'Trad.',
  audioUrl: 'https://soundcloud.com/example/e2e',
  eventName: 'E2E 確認イベント',
  /* リリース日と別の日にする。同じ日にすると、整形後の表示がどちらの日付か区別できない */
  eventDate: '2026-08-13',
  /** Markdown として描画されることを、要素ごとに確かめるための断片 */
  description: {
    heading: '概要',
    lead: 'E2E で画面を確認するための作品。',
    bullet: '箇条書き',
    emphasis: '強調',
  },
} as const;

/**
 * 外部音源を持たない作品。
 *
 * <p>
 * カバー画像とプレイヤーの出し分け（#197）は、音源が0件の側も見なければ検証にならない。ISDN と
 * 初出イベントの5項目も、この作品で確かめる。
 * </p>
 */
export const quiet = {
  catalogNumber: 'E2E-0002',
  isdn: '2784000001004',
  title: 'E2E 音源なしアルバム',
  artistDisplayName: 'E2E 音源なしアーティスト',
  releaseDate: '2026-08-16',
  trackTitle: 'E2E 音源なしトラック',
  /** Markdown として解釈されないことを見るため、記法の見た目を含める */
  description: 'プレーンテキストの概要説明。**強調** は記法にならない。',
  event: {
    name: 'E2E 音源なしイベント',
    /* リリース日と別の日にする。同じ日にすると time[datetime] がどちらの日付か区別できない */
    date: '2026-08-14',
    place: 'E2E 会場',
    spaceNumber: 'B-02',
    note: 'E2E 音源なしの補足',
  },
} as const;

/** 下書きのまま置く作品。公開の一覧・詳細のどちらにも出てはいけない */
export const draft = {
  catalogNumber: 'E2E-0003',
  title: 'E2E 下書きアルバム',
  artistDisplayName: 'E2E 下書きアーティスト',
  releaseDate: '2026-08-17',
} as const;

/** 作品を紹介する記事。参照先への導線とタグを確かめる */
export const albumArticle = {
  title: 'E2E 作品紹介記事',
  introShort: 'E2E で一覧のカードを確かめるためのショート紹介文。',
  tags: ['E2E タグA', 'E2E タグB'],
  /** Markdown として描画されることを、要素ごとに確かめるための断片 */
  body: {
    heading: '記事の見出し',
    lead: 'E2E で記事の本文を確かめる。',
  },
} as const;

/** 作品への参照を持たない記事 */
export const plainArticle = {
  title: 'E2E ノート記事',
  introShort: '作品を参照しない記事のショート紹介文。',
  /** Markdown として解釈されないことを見るため、記法の見た目を含める */
  body: 'プレーンテキストの本文。**強調** は記法にならない。',
} as const;

/** 下書きのまま置く記事。公開の一覧・詳細のどちらにも出てはいけない */
export const draftArticle = {
  title: 'E2E 下書き記事',
} as const;

/**
 * サイトの文言（#230）。
 *
 * <p>
 * 文言はリポジトリに置かず管理画面から入れるため、画面に出る文字列はここが唯一の出所になる。未登録の
 * キーは区画ごと出ない仕様のため、E2E では3つとも入れて「出る」側を確かめる。
 * </p>
 */
export const siteContent = {
  name: 'E2E 確認サイト',
  description: 'E2E で画面を確認するためのサイト。',
  /** Markdown として描画されることを、要素ごとに確かめるための断片 */
  introduction: {
    heading: 'ようこそ',
    lead: 'E2E でトップの紹介文を確かめる。',
  },
} as const;

/**
 * ページ送りを確かめるための記事。
 *
 * <p>
 * 1ページの件数は画面が決める（`$lib/pagination.ts` の `ARTICLES_PER_PAGE`）。ここはその値と揃え、
 * 公開する記事の総数が1ページに収まらない数になるよう置く。ずれたらページ送りのシナリオが落ちるため、
 * 揃っていないことに気付ける。
 * </p>
 */
export const pagination = {
  /** 画面が1ページに並べる件数 */
  perPage: 20,
  /** 作品紹介・ノートの2件と合わせて1ページを1件だけ超える */
  filler: 19,
  titleOf: (index: number): string => `E2E ページ送り記事 ${String(index)}`,
} as const;

const showcaseSeed: AlbumSeed = {
  title: showcase.title,
  releaseDate: showcase.releaseDate,
  artistDisplayName: showcase.artistDisplayName,
  artistSortKey: 'いーつーいーかくにん',
  catalogNumber: showcase.catalogNumber,
  description: [
    `## ${showcase.description.heading}`,
    '',
    showcase.description.lead,
    '',
    `- ${showcase.description.bullet}`,
    `- **${showcase.description.emphasis}**`,
    '',
  ].join('\n'),
  descriptionFormat: 'MARKDOWN',
  event: {
    name: showcase.eventName,
    date: showcase.eventDate,
    place: 'E2E 会場',
    spaceNumber: 'A-01',
  },
  tracks: [
    {
      trackNo: 1,
      title: showcase.trackTitle,
      tunes: [
        { seq: 1, tuneTitle: showcase.tuneTitle, composerCreditOverride: showcase.composerCredit },
      ],
    },
  ],
  externalAudioUrls: [showcase.audioUrl],
};

const quietSeed: AlbumSeed = {
  title: quiet.title,
  releaseDate: quiet.releaseDate,
  artistDisplayName: quiet.artistDisplayName,
  artistSortKey: 'いーつーおんげんなし',
  catalogNumber: quiet.catalogNumber,
  isdn: quiet.isdn,
  description: quiet.description,
  descriptionFormat: 'PLAIN_TEXT',
  event: quiet.event,
  tracks: [{ trackNo: 1, title: quiet.trackTitle }],
};

const draftSeed: AlbumSeed = {
  title: draft.title,
  releaseDate: draft.releaseDate,
  artistDisplayName: draft.artistDisplayName,
  artistSortKey: 'いーつーしたがき',
  catalogNumber: draft.catalogNumber,
};

const albumArticleSeed = (albumId: string): ArticleSeed => ({
  articleType: 'ALBUM',
  title: albumArticle.title,
  body: [`## ${albumArticle.body.heading}`, '', albumArticle.body.lead, ''].join('\n'),
  bodyFormat: 'MARKDOWN',
  introShort: albumArticle.introShort,
  albumId,
  tags: albumArticle.tags,
});

const plainArticleSeed: ArticleSeed = {
  articleType: 'NOTE',
  title: plainArticle.title,
  body: plainArticle.body,
  bodyFormat: 'PLAIN_TEXT',
  introShort: plainArticle.introShort,
};

const draftArticleSeed: ArticleSeed = {
  articleType: 'NEWS',
  title: draftArticle.title,
};

const fillerArticleSeed = (index: number): ArticleSeed => ({
  articleType: 'NOTE',
  title: pagination.titleOf(index),
  introShort: 'ページ送りを確かめるための記事。',
});

/** 公開まで済ませるか、下書きで置くか */
type SeedState = 'PUBLISHED' | 'DRAFT';

/**
 * 既存の作品の公開状態を指定へ揃える。
 *
 * <p>
 * 作成と公開は別のリクエストのため、間で中断すると公開予定のものが下書きのまま残る。状態だけは毎回
 * 揃えることで、次の実行が自力で直せるようにする。
 * </p>
 *
 * <p>
 * 内容（タイトル・イベント・曲目など）は揃えない。フィクスチャの値を変えたときは作り直しが要るが、
 * トラックを持つ作品の削除が塞がっている（#251）。解消後に、記事と同じ作り直しへ移す。
 * </p>
 */
const alignAlbumPublishState = async (
  publishedAt: string | null,
  albumId: string,
  state: SeedState,
): Promise<void> => {
  const current: SeedState = publishedAt === null ? 'DRAFT' : 'PUBLISHED';

  return current === state
    ? undefined
    : state === 'PUBLISHED'
      ? publishAlbum(albumId)
      : unpublishAlbum(albumId);
};

const ensureAlbum = async (
  catalogNumber: string,
  seed: AlbumSeed,
  state: SeedState,
): Promise<void> => {
  const existing = await findAlbumByCatalogNumber(catalogNumber);

  return existing === undefined
    ? seedDraftAlbum(seed).then((albumId) =>
        state === 'PUBLISHED' ? publishAlbum(albumId) : undefined,
      )
    : alignAlbumPublishState(existing.publishedAt, existing.albumId, state);
};

/**
 * 記事を作り直す。
 *
 * <p>
 * 記事は子を持たないため削除できる。あれば消してから作ることで、内容まで毎回同じ状態になる。作品への
 * 参照やタグの付与は作成とは別のリクエストのため、途中で中断すると欠けたまま残る。存在するだけで
 * 成功扱いにすると、その欠けた記事を以後の実行が直せない。
 * </p>
 */
const ensureArticle = async (seed: ArticleSeed, state: SeedState): Promise<void> => {
  const existing = await findArticleByTitle(seed.title);
  await (existing === undefined ? Promise.resolve() : deleteArticle(existing.articleId));

  const articleId = await seedDraftArticle(seed);
  return state === 'PUBLISHED' ? publishArticle(articleId) : undefined;
};

/**
 * 画面確認用のデータを揃える。
 *
 * <p>
 * SEQUENTIAL-ORDER: 記事の一覧は公開日の降順で並ぶ（#197）。公開の順序がそのまま並び順になるため、
 * 1ページ目の先頭に置きたいものを最後に公開する。ページ送りの詰め物を先に、作品紹介の記事を最後に置くのは
 * このためで、失敗したときにどれを作れなかったのかを追える利点も兼ねる。
 * </p>
 *
 * <p>
 * 作品紹介の記事だけは参照先の作品を要するため、作品を揃えたあとに作る。
 * </p>
 */
export const seedForBuild = async (): Promise<void> => {
  await upsertSiteContent({
    key: 'site.name',
    content: siteContent.name,
    contentFormat: 'PLAIN_TEXT',
  });
  await upsertSiteContent({
    key: 'site.description',
    content: siteContent.description,
    contentFormat: 'PLAIN_TEXT',
  });
  await upsertSiteContent({
    key: 'home.introduction',
    content: [`## ${siteContent.introduction.heading}`, '', siteContent.introduction.lead, ''].join(
      '\n',
    ),
    contentFormat: 'MARKDOWN',
  });

  await ensureAlbum(showcase.catalogNumber, showcaseSeed, 'PUBLISHED');
  await ensureAlbum(quiet.catalogNumber, quietSeed, 'PUBLISHED');
  await ensureAlbum(draft.catalogNumber, draftSeed, 'DRAFT');

  const showcaseAlbum = await findAlbumByCatalogNumber(showcase.catalogNumber);
  const showcaseAlbumId =
    showcaseAlbum === undefined
      ? await Promise.reject(new Error(`シードした作品が見つかりません: ${showcase.catalogNumber}`))
      : showcaseAlbum.albumId;

  for (const index of Array.from({ length: pagination.filler }, (_unused, i) => i + 1)) {
    await ensureArticle(fillerArticleSeed(index), 'PUBLISHED');
  }

  await ensureArticle(plainArticleSeed, 'PUBLISHED');
  await ensureArticle(albumArticleSeed(showcaseAlbumId), 'PUBLISHED');
  await ensureArticle(draftArticleSeed, 'DRAFT');
};
