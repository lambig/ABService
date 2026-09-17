import { coverImageAsset } from './cover-image.ts';
import {
  deleteArticle,
  ensureAlbumCoverImage,
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
 * （削除が塞がっている。#251）。showcaseのカバー画像は旧シードに無いため、既存作品にも不足を補う。
 * 記事は削除できるため、あれば消してから作り直し、内容まで揃える。
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
  audioUrl: 'https://soundcloud.com/example/e2e',
  eventName: 'E2E 確認イベント',
  /* リリース日と別の日にする。同じ日にすると、整形後の表示がどちらの日付か区別できない */
  eventDate: '2026-08-13',
  eventPlace: 'E2E 会場',
  eventSpaceNumber: 'A-01',
  /** 頒布サークル名。名義と別に持ち、頒布イベントの行の末尾に出る（#415） */
  eventCircleName: 'E2E 頒布サークル',
  /** 頒布の基準額（#349）。作品紹介の記事にだけ出る */
  basePrice: 1500,
  /** 画面に出る形（ラベル込み）。投入値と並べて置き、整形の結果をシナリオから読めるようにする */
  basePriceText: '頒布価格 1,500円',
  /**
   * 原作の出典の記述（#365）。
   *
   * 作品名と言い回しがひとつながりの一文で、構造としては分けていない。トラックとの対応も述べていない
   * （この作品は8つのトラックを持つが、記述はどのトラックがどれに当たるかを言わない）。
   */
  originalWorkNote: '「E2E 原作ゲーム」より各曲',
  /** Markdown として描画されることを、要素ごとに確かめるための断片 */
  description: {
    heading: 'コンセプト',
    lead: 'E2E で画面を確認するための作品。',
    bullet: '箇条書き',
    emphasis: '強調',
  },
} as const;

/**
 * `showcase` の曲目（#360）。
 *
 * <p>
 * トラック名とチューンの組み合わせを網羅する。1つの作品にまとめるのは、**並び方と名の出かたを同じ画面で
 * 見比べるため**で、作品を分けると証跡も分かれて比較できない。
 * </p>
 *
 * <p>
 * `name` は画面に出る名。トラック名を持つトラックはその名、持たないトラックはチューン名を繋いだものになる。
 * 繋ぎの区切りはバックエンドの設定（`abservice.track.tune-title-separator`）が持ち、その既定を
 * {@link TUNE_TITLE_SEPARATOR} に写している。名を持たないチューン（MC・環境音）は名に現れない。
 * さらに、名もクレジットも持たないチューンは曲目の行にも出ない——位置だけを表す空の行になるため。
 * </p>
 */
export const TUNE_TITLE_SEPARATOR = ' / ';

const joinTuneTitles = (...titles: readonly string[]): string => titles.join(TUNE_TITLE_SEPARATOR);

export const showcaseTracks = {
  /** 名あり・チューン1件（作曲のクレジット） */
  titledWithTune: {
    title: 'E2E 確認トラック',
    tuneTitle: 'E2E 確認チューン',
    composerCredit: 'Trad.',
    get name(): string {
      return this.title;
    },
  },
  /** 名あり・チューンなし */
  titledWithoutTunes: {
    title: 'E2E 単独トラック',
    get name(): string {
      return this.title;
    },
  },
  /** 名あり・チューン1件（作曲と編曲の両方のクレジット） */
  titledWithArrangedTune: {
    title: 'E2E 編曲トラック',
    tuneTitle: 'E2E 編曲チューン',
    composerCredit: 'E2E 作曲者',
    arrangerCredit: 'E2E 編曲者',
    get name(): string {
      return this.title;
    },
  },
  /** 名あり・チューン複数（1件目だけクレジットを持つ） */
  titledWithTunes: {
    title: 'E2E 組曲トラック',
    firstTuneTitle: 'E2E 組曲チューン1',
    secondTuneTitle: 'E2E 組曲チューン2',
    composerCredit: 'E2E 組曲作曲者',
    get name(): string {
      return this.title;
    },
  },
  /** 名なし・チューン1件 */
  untitledWithTune: {
    tuneTitle: 'E2E 名なしトラックのチューン',
    get name(): string {
      return this.tuneTitle;
    },
  },
  /** 名なし・チューン複数（区切りで繋がる） */
  untitledWithTunes: {
    firstTuneTitle: 'E2E 連結チューンA',
    secondTuneTitle: 'E2E 連結チューンB',
    get name(): string {
      return joinTuneTitles(this.firstTuneTitle, this.secondTuneTitle);
    },
  },
  /** 名なし・名もクレジットも持たないチューンを挟む（そのチューンは名にも曲目の行にも出ない） */
  untitledWithUnnamedTune: {
    firstTuneTitle: 'E2E 間奏前チューン',
    lastTuneTitle: 'E2E 間奏後チューン',
    get name(): string {
      return joinTuneTitles(this.firstTuneTitle, this.lastTuneTitle);
    },
  },
  /** 名あり・名を持たないチューンだけ（名の元はトラック名しかない。クレジットを持つので行は出る） */
  titledWithUnnamedTune: {
    title: 'E2E MCトラック',
    tuneCredit: 'E2E 語り',
    get name(): string {
      return this.title;
    },
  },
} as const;

/**
 * 曲目に並ぶ名を、並ぶ順に並べたもの。
 *
 * この定義の順がそのままシードの並びで、シードの並びがそのままトラック番号になる（#391）。番号で並べ直す
 * 手順を持たないのは、番号がどこにも入力として現れないためである。
 */
export const showcaseTrackNames: readonly string[] = Object.values(showcaseTracks).map(
  (track) => track.name,
);

/**
 * 外部音源を持たず、カバー画像を持つ作品。
 *
 * <p>
 * カバー画像とプレイヤーの出し分け（#197）は、音源が0件の側も見なければ検証にならない。ISDN と
 * 初出イベントの5項目も、この作品で確かめる。
 * </p>
 *
 * <p>
 * `quiet` は画像だけ、`showcase` は画像と音源の両方、`coverless` はどちらも持たない（#380）。
 * 画像を本当に持つ作品でプレイヤー優先を検査し、画像無しの区画も同じ実行で確認する。
 * </p>
 *
 * <p>
 * 頒布の基準額は持たせない。額を持たない作品では記事に額の区画が出ないことを、この作品を参照する
 * 記事で見る（#349）。
 * </p>
 *
 * <p>
 * 原作の出典の記述も持たせない。記述を持たない作品では、その行ごと出ないことをこの作品で見る（#365）。
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
    /** 画面に出る形。投入値と並べて置き、整形の結果をシナリオから読めるようにする */
    dateText: '2026年8月14日',
    place: 'E2E 会場',
    spaceNumber: 'B-02',
    circleName: 'E2E 音源なし合同',
    note: 'E2E 音源なしの補足',
  },
} as const;

/** 画像と音源を持たない公開作品。画像の区画を出さない側を受け持つ。 */
export const coverless = {
  catalogNumber: 'E2E-0000',
  title: 'E2E カバーなしアルバム',
  artistDisplayName: 'E2E カバーなしアーティスト',
  releaseDate: '2026-08-12',
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

/**
 * 本文に画像を持つ記事。作品を参照しないノート。
 *
 * <p>
 * 画像の配信ベース判定を確かめるための入力。配信ベース配下のものは描かれ、`/assets/../api/...` のように
 * 解決後は配下から出るものは画像ごと落ちる（#289）。**プレビューと公開が同じ設定で判定していること**を
 * 同じ本文から見るため、1つの記事の本文へ両方を入れておく。
 * </p>
 *
 * <p>
 * 作品紹介記事（`albumArticle`）には入れない。実体を置いていないため描かれる側も壊れ画像として写り、
 * 記事の体裁を見る証跡の邪魔になる。本文画像の運用はアセットエクスプローラ（#213）まで始めないため、
 * 検査の入力はこの記事に閉じ込める。
 * </p>
 */
export const imageArticle = {
  title: 'E2E 本文画像の記事',
  introShort: '本文の画像の配信パス判定を確かめるための記事。',
  body: {
    heading: '本文画像の見出し',
  },
  image: {
    allowedSrc: '/assets/e2e-body-image.png',
    allowedAlt: 'E2E 本文の画像',
    deviantSrc: '/assets/../api/v1/albums',
    deviantAlt: 'E2E 配下から出る画像',
  },
} as const;

/**
 * 額を持たない作品を紹介する記事。
 *
 * 額の区画が出るかどうかは参照先の作品で決まるため、額を持つ側（`albumArticle`）と持たない側の
 * 両方を1回の実行で見る（#349）。
 */
export const quietArticle = {
  title: 'E2E 額なし作品紹介記事',
  introShort: '額を持たない作品を紹介する記事のショート紹介文。',
} as const;

/** 画像を持たない作品を参照する記事。参照の有無と画像の有無を分けて検査する。 */
export const coverlessArticle = {
  title: 'E2E カバーなし作品紹介記事',
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
 * キーは区画ごと出ない仕様のため、E2E では入れて「出る」側を確かめる。
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
  /**
   * コピーライト表示の主体（#343）。
   *
   * 画面に出るのは、これに年と記号を足して組み立てたもの。データ側が持つのは主体だけで、年は
   * 組み立てる時点のものになる。
   */
  copyrightHolder: 'E2E 確認サークル',
  /**
   * 既定の名義（#348）。
   *
   * 画面に名義が出るのは既定と違うときだけ。`showcase` をこの名義に揃え、`quiet` を別の名義のまま
   * 残すことで、出る側と出ない側の両方を1回の実行で見る。
   */
  defaultArtist: showcase.artistDisplayName,
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
  /** 作品紹介3件・ノート2件と合わせて1ページを1件だけ超える */
  filler: 16,
  titleOf: (index: number): string => `E2E ページ送り記事 ${String(index)}`,
} as const;

/**
 * 名のある記事を1件増やすたびに廃止した詰め物。旧DBからこれらの予約済みタイトルだけを片付ける
 * （#380 で作品紹介を、#415 で本文画像の記事を増やした）。
 */
export const retiredPaginationArticleTitles = [
  pagination.titleOf(18),
  pagination.titleOf(17),
] as const;

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
    place: showcase.eventPlace,
    spaceNumber: showcase.eventSpaceNumber,
    circleName: showcase.eventCircleName,
  },
  basePrice: { amount: showcase.basePrice },
  originalWorkNote: showcase.originalWorkNote,
  tracks: [
    {
      title: showcaseTracks.titledWithTune.title,
      tunes: [
        {
          tuneTitle: showcaseTracks.titledWithTune.tuneTitle,
          composerCreditOverride: showcaseTracks.titledWithTune.composerCredit,
        },
      ],
    },
    {
      title: showcaseTracks.titledWithoutTunes.title,
    },
    {
      title: showcaseTracks.titledWithArrangedTune.title,
      tunes: [
        {
          tuneTitle: showcaseTracks.titledWithArrangedTune.tuneTitle,
          composerCreditOverride: showcaseTracks.titledWithArrangedTune.composerCredit,
          arrangerCreditOverride: showcaseTracks.titledWithArrangedTune.arrangerCredit,
        },
      ],
    },
    {
      title: showcaseTracks.titledWithTunes.title,
      tunes: [
        {
          tuneTitle: showcaseTracks.titledWithTunes.firstTuneTitle,
          composerCreditOverride: showcaseTracks.titledWithTunes.composerCredit,
        },
        { tuneTitle: showcaseTracks.titledWithTunes.secondTuneTitle },
      ],
    },
    /* ここから下はトラック名を持たない。名はチューン名から決まる（#360） */
    {
      tunes: [{ tuneTitle: showcaseTracks.untitledWithTune.tuneTitle }],
    },
    {
      tunes: [
        { tuneTitle: showcaseTracks.untitledWithTunes.firstTuneTitle },
        { tuneTitle: showcaseTracks.untitledWithTunes.secondTuneTitle },
      ],
    },
    {
      tunes: [
        { tuneTitle: showcaseTracks.untitledWithUnnamedTune.firstTuneTitle },
        /* 名もクレジットも持たない間奏。名にも曲目の行にも出ない（#360） */
        {},
        { tuneTitle: showcaseTracks.untitledWithUnnamedTune.lastTuneTitle },
      ],
    },
    {
      title: showcaseTracks.titledWithUnnamedTune.title,
      tunes: [{ composerCreditOverride: showcaseTracks.titledWithUnnamedTune.tuneCredit }],
    },
  ],
  externalAudioUrls: [showcase.audioUrl],
  coverImage: coverImageAsset,
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
  coverImage: coverImageAsset,
  tracks: [{ title: quiet.trackTitle }],
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

const imageArticleSeed: ArticleSeed = {
  articleType: 'NOTE',
  title: imageArticle.title,
  body: [
    `## ${imageArticle.body.heading}`,
    '',
    `![${imageArticle.image.allowedAlt}](${imageArticle.image.allowedSrc})`,
    '',
    `![${imageArticle.image.deviantAlt}](${imageArticle.image.deviantSrc})`,
    '',
  ].join('\n'),
  bodyFormat: 'MARKDOWN',
  introShort: imageArticle.introShort,
};

const quietArticleSeed = (albumId: string): ArticleSeed => ({
  articleType: 'ALBUM',
  title: quietArticle.title,
  introShort: quietArticle.introShort,
  albumId,
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

/** シードした作品のID。揃えたはずの作品が無いのは前提が崩れているため、続けずに落とす */
const seededAlbumId = async (catalogNumber: string): Promise<string> => {
  const album = await findAlbumByCatalogNumber(catalogNumber);

  return album === undefined
    ? Promise.reject(new Error(`シードした作品が見つかりません: ${catalogNumber}`))
    : album.albumId;
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
 * 作品紹介の記事は参照先の作品を要するため、作品を揃えたあとに作る。
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
  await upsertSiteContent({
    key: 'site.artist',
    content: siteContent.defaultArtist,
    contentFormat: 'PLAIN_TEXT',
  });
  await upsertSiteContent({
    key: 'footer.copyright.holder',
    content: siteContent.copyrightHolder,
    contentFormat: 'PLAIN_TEXT',
  });

  await ensureAlbum(showcase.catalogNumber, showcaseSeed, 'PUBLISHED');
  await ensureAlbum(quiet.catalogNumber, quietSeed, 'PUBLISHED');
  await ensureAlbum(draft.catalogNumber, draftSeed, 'DRAFT');
  await ensureAlbum(
    coverless.catalogNumber,
    { ...coverless, artistSortKey: 'いーつーいーかばーなし' },
    'PUBLISHED',
  );

  const showcaseAlbumId = await seededAlbumId(showcase.catalogNumber);
  const quietAlbumId = await seededAlbumId(quiet.catalogNumber);
  const coverlessAlbumId = await seededAlbumId(coverless.catalogNumber);

  await ensureAlbumCoverImage(showcaseAlbumId, coverImageAsset);

  for (const title of retiredPaginationArticleTitles) {
    const retired = await findArticleByTitle(title);
    await (retired === undefined ? Promise.resolve() : deleteArticle(retired.articleId));
  }

  for (const index of Array.from({ length: pagination.filler }, (_unused, i) => i + 1)) {
    await ensureArticle(fillerArticleSeed(index), 'PUBLISHED');
  }

  await ensureArticle(imageArticleSeed, 'PUBLISHED');
  await ensureArticle(
    { articleType: 'ALBUM', title: coverlessArticle.title, albumId: coverlessAlbumId },
    'PUBLISHED',
  );
  await ensureArticle(quietArticleSeed(quietAlbumId), 'PUBLISHED');
  await ensureArticle(plainArticleSeed, 'PUBLISHED');
  await ensureArticle(albumArticleSeed(showcaseAlbumId), 'PUBLISHED');
  await ensureArticle(draftArticleSeed, 'DRAFT');
};
