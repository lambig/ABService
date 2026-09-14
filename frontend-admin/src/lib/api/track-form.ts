import type { AdminTrack, TrackFields } from './client';

/**
 * チューン構成1行の入力。
 *
 * <p>
 * 欄はすべて文字列で持つ。空の欄は「持たない」として送る——空文字を送ると、値があって空である状態と
 * 区別できなくなる。
 * </p>
 */
export interface TuneDraft {
  readonly tuneTitle: string;
  readonly composerCreditOverride: string;
  readonly arrangerCreditOverride: string;
  readonly linkUrl: string;
}

/**
 * 曲目1行の入力。
 *
 * <p>
 * <b>トラック番号もチューンの登場順も持たない</b>——並びは配列の位置がそのまま表す（#391）。`trackId` は
 * 既にあるトラックを指し、足したばかりの行は持たない。送らなかった既存の行は、保存の時点で消える。
 * </p>
 */
export interface TrackDraft {
  readonly trackId: string | null;
  readonly title: string;
  readonly artistDisplayName: string;
  readonly artistSortKey: string;
  readonly tunes: readonly TuneDraft[];
}

export const EMPTY_TUNE: TuneDraft = {
  tuneTitle: '',
  composerCreditOverride: '',
  arrangerCreditOverride: '',
  linkUrl: '',
};

export const EMPTY_TRACK: TrackDraft = {
  trackId: null,
  title: '',
  artistDisplayName: '',
  artistSortKey: '',
  tunes: [],
};

/** 読み込んだトラックを入力へ写す */
export const trackDraftOf = (track: AdminTrack): TrackDraft => ({
  trackId: track.trackId,
  title: track.title,
  artistDisplayName: track.artistDisplayName ?? '',
  artistSortKey: track.artistSortKey ?? '',
  tunes: track.tunes.map((tune) => ({
    tuneTitle: tune.tuneTitle ?? '',
    composerCreditOverride: tune.composerCreditOverride ?? '',
    arrangerCreditOverride: tune.arrangerCreditOverride ?? '',
    linkUrl: tune.linkUrl ?? '',
  })),
});

/** 空の欄は送らない。省いた項目は「持たない」になる */
const presence = (value: string): string | undefined => (value === '' ? undefined : value);

/**
 * 送る形へ組み立てる。
 *
 * <p>
 * 番号は組み立てない。トラック番号もチューンの登場順も、送った配列の位置から集約が振る（#391）。
 * 足したばかりの行はIDを持たないため、要求の契約でも省略として表す。
 * </p>
 */
export const trackFieldsOf = (draft: TrackDraft): TrackFields => ({
  trackId: draft.trackId ?? undefined,
  title: presence(draft.title),
  artistDisplayName: presence(draft.artistDisplayName),
  artistSortKey: presence(draft.artistSortKey),
  tunes: draft.tunes.map((tune) => ({
    tuneTitle: presence(tune.tuneTitle),
    composerCreditOverride: presence(tune.composerCreditOverride),
    arrangerCreditOverride: presence(tune.arrangerCreditOverride),
    linkUrl: presence(tune.linkUrl),
  })),
});

/**
 * 畳んだ行に出す文言の材料。
 *
 * 入力（空文字で持つ）と、読み込んだもの（`null` で持つ）の両方から作れる形にする。畳んだ見え方を
 * 2通り書くと、入力の側と一覧の側でずれる。
 */
export interface TuneSummarySource {
  readonly tuneTitle: string | null;
  readonly composerCreditOverride: string | null;
  readonly arrangerCreditOverride: string | null;
}

/** 空文字と `null` はどちらも「持たない」 */
const shown = (value: string | null): string | null => ((value ?? '') === '' ? null : value);

/**
 * 畳んだチューンの行に出す文言。
 *
 * <p>
 * 公開サイトと同じ読み方（曲名とクレジット）で出す。**空の行も落とさない**——落とすと、入っている
 * ものが画面から消えて編集できなくなる（公開は読めない行を落とすが、ここでは触れる必要がある）。
 * </p>
 */
export const tuneSummaryOf = (tune: TuneSummarySource): string => {
  const credits = [
    shown(tune.composerCreditOverride) === null
      ? null
      : `作曲: ${String(tune.composerCreditOverride)}`,
    shown(tune.arrangerCreditOverride) === null
      ? null
      : `編曲: ${String(tune.arrangerCreditOverride)}`,
  ].filter((credit) => credit !== null);
  const credit = credits.length === 0 ? '' : `（${credits.join(' / ')}）`;
  const summary = `${shown(tune.tuneTitle) ?? ''}${credit}`;

  return summary === '' ? '（空の行）' : summary;
};

/**
 * 畳んだトラックの行に出すタイトル。
 *
 * <p>
 * タイトルを省いたトラックの名は、チューン名を繋いだものになる（#360）。<b>繋ぎ方は画面が決め打たない</b>
 * ——区切りはバックエンドの設定が持っており、写すと2箇所へ散る。省かれていることだけを示し、名の材料は
 * その下に並ぶチューンの行が担う。
 * </p>
 */
export const trackTitleOf = (draft: TrackDraft): string =>
  draft.title === '' ? '（チューン名から組まれます）' : draft.title;

/** トラックの欄の位置。作品の入力パスの中では `tracks[i].` を冠して現れる */
export const TRACK_FIELDS = ['title', 'artistDisplayName', 'artistSortKey'] as const;

export type TrackField = (typeof TRACK_FIELDS)[number];

/** チューンの行の欄の位置 */
export const TUNE_FIELDS = [
  'tuneTitle',
  'composerCreditOverride',
  'arrangerCreditOverride',
  'linkUrl',
] as const;

export type TuneField = (typeof TUNE_FIELDS)[number];

/** 曲目の行を指す位置の接頭辞。行の位置は並びが変われば別の行を指す */
export const TRACK_PATH_PREFIX = 'tracks[';

export const trackPathOf = (index: number, field: TrackField): string =>
  `${TRACK_PATH_PREFIX}${String(index)}].${field}`;

export const tunePathOf = (trackIndex: number, tuneIndex: number, field: TuneField): string =>
  `${TRACK_PATH_PREFIX}${String(trackIndex)}].tunes[${String(tuneIndex)}].${field}`;

/**
 * いまの入力で欄へ割り当てられる位置。
 *
 * <p>
 * 行の数だけ添字が増えるため、入力から組み立てる。ここに無い位置のエラーは捨てず、位置を添えて全体の
 * エラーとして出す（DECISIONS 29）——行そのものが無いことを指す `tracks[0]` は、欄を持たないためそちら
 * へ回る。
 * </p>
 */
export const trackPathsOf = (tracks: readonly TrackDraft[]): readonly string[] =>
  tracks.flatMap((track, index) => [
    ...TRACK_FIELDS.map((field) => trackPathOf(index, field)),
    ...track.tunes.flatMap((_tune, tuneIndex) =>
      TUNE_FIELDS.map((field) => tunePathOf(index, tuneIndex, field)),
    ),
  ]);
