import {
  findAlbumByCatalogNumber,
  publishAlbum,
  seedDraftAlbum,
  unpublishAlbum,
} from './admin-api.ts';
import type { AdminAlbum, AlbumSeed } from './admin-api.ts';

/**
 * 公開サイトを組む前に入れておくデータ。
 *
 * <p>
 * 公開サイトは静的出力で、ビルド時の内容がそのまま HTML になる（DECISIONS 24）。シナリオの中で投入した
 * データは**その回のビルドには入らない**ため、画面に現れてほしいものはここへ置く。
 * </p>
 *
 * <p>
 * 投入は冪等にする。ローカルでは同じ DB へ繰り返し実行するため、毎回作ると同じ作品が並んで証跡が
 * 読みにくくなる。カタログナンバーで存在を見て、無いときだけ作る。あわせて公開状態は毎回揃える。
 * 作成と公開の間で中断すると下書きのまま残り、以後の実行が自力で直せなくなるため。
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
    date: showcase.releaseDate,
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

/** 公開まで済ませるか、下書きで置くか */
type SeedState = 'PUBLISHED' | 'DRAFT';

/**
 * 既存の作品の公開状態を指定へ揃える。
 *
 * <p>
 * 作成と公開は別のリクエストのため、間で中断すると公開予定の作品が下書きのまま残る。状態だけは毎回
 * 揃えることで、次の実行が自力で直せるようにする。
 * </p>
 *
 * <p>
 * 内容（タイトル・イベント・曲目など）は揃えない。フィクスチャの値を変えたときは作り直しが要るが、
 * トラックを持つ作品の削除が塞がっている（#251）。解消後に、専用データを削除して作り直す形へ移す。
 * </p>
 */
const alignPublishState = async (album: AdminAlbum, state: SeedState): Promise<void> => {
  const current: SeedState = album.publishedAt === null ? 'DRAFT' : 'PUBLISHED';

  return current === state
    ? undefined
    : state === 'PUBLISHED'
      ? publishAlbum(album.albumId)
      : unpublishAlbum(album.albumId);
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
    : alignPublishState(existing, state);
};

/**
 * 画面確認用のデータを揃える。
 *
 * <p>
 * SEQUENTIAL-ORDER: 一覧はカタログナンバーの降順で並ぶ（#197）。同定は番号で行うため投入の順序は
 * 結果に効かないが、失敗したときにどれを作れなかったのかを追えるよう1件ずつ送る。
 * </p>
 */
export const seedForBuild = async (): Promise<void> => {
  await ensureAlbum(showcase.catalogNumber, showcaseSeed, 'PUBLISHED');
  await ensureAlbum(quiet.catalogNumber, quietSeed, 'PUBLISHED');
  await ensureAlbum(draft.catalogNumber, draftSeed, 'DRAFT');
};
