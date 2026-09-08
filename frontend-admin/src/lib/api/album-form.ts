import type { AdminAlbumDetail, AlbumFields } from './client';

/**
 * 入力欄の位置。
 *
 * <p>
 * 綴りは**管理APIが検証エラーの `field` として返す入力パス**に揃える。画面の内部名を別に持つと、
 * 応答の位置から欄を引くための対応表が要り、検証を1つ足すたびに2箇所を変えることになる（DECISIONS 29）。
 * </p>
 */
export const ALBUM_FIELD_PATHS = [
  'title',
  'releaseDate',
  'artistDisplayName',
  'artistSortKey',
  'catalogNumber',
  'isdn',
  'description',
  'descriptionFormat',
  'coverImageKey',
  'event.name',
  'event.date',
  'event.place',
  'event.spaceNumber',
  'event.note',
] as const;

/** 入力欄の位置 */
export type AlbumFieldPath = (typeof ALBUM_FIELD_PATHS)[number];

/**
 * 編集中の入力値。
 *
 * <p>
 * 未入力は空文字で持ち、`null` と `undefined` を混ぜない。どちらも「入力されていない」を表せてしまうと、
 * 欄の値を読む側が両方を見る必要が出る。API へ渡す段で空文字を未指定（項目を送らない）へ写す。
 * </p>
 */
export type AlbumDraft = Readonly<Record<AlbumFieldPath, string>>;

/** 概要説明のマークアップ形式。`description` を指定するときだけ意味を持つ */
export const DESCRIPTION_FORMATS = ['PLAIN_TEXT', 'MARKDOWN'] as const;

/** 新規作成の初期値。形式だけは既定を持つ（選択肢のどれでもない状態を作らない） */
export const EMPTY_DRAFT: AlbumDraft = {
  title: '',
  releaseDate: '',
  artistDisplayName: '',
  artistSortKey: '',
  catalogNumber: '',
  isdn: '',
  description: '',
  descriptionFormat: 'PLAIN_TEXT',
  coverImageKey: '',
  'event.name': '',
  'event.date': '',
  'event.place': '',
  'event.spaceNumber': '',
  'event.note': '',
};

/**
 * 既存の作品を編集の初期値へ写す。
 *
 * <p>
 * 照会の応答は初出イベントを平らな項目（`eventName` 等）で返し、更新の要求は入れ子（`event.name`）で
 * 受ける。位置の綴りは要求側に揃える（検証エラーの `field` が要求側の綴りで返るため）。
 * </p>
 */
export const draftOf = (album: AdminAlbumDetail): AlbumDraft => ({
  title: album.title,
  releaseDate: album.releaseDate,
  artistDisplayName: album.artistDisplayName,
  artistSortKey: album.artistSortKey ?? '',
  catalogNumber: album.catalogNumber ?? '',
  isdn: album.isdn ?? '',
  description: album.description ?? '',
  descriptionFormat: album.descriptionFormat,
  coverImageKey: album.coverImageKey ?? '',
  'event.name': album.eventName ?? '',
  'event.date': album.eventDate ?? '',
  'event.place': album.eventPlace ?? '',
  'event.spaceNumber': album.eventSpaceNumber ?? '',
  'event.note': album.eventNote ?? '',
});

/** 1つの欄だけを差し替えた入力値を返す */
export const withValue = (draft: AlbumDraft, path: AlbumFieldPath, value: string): AlbumDraft => ({
  ...draft,
  [path]: value,
});

const blankToUndefined = (value: string): string | undefined => (value === '' ? undefined : value);

const presence = (value: string): string | undefined => blankToUndefined(value.trim());

/**
 * 初出イベント。
 *
 * <p>
 * どの項目も入力されていなければ、イベント自体を送らない（更新は全項目置換のため、これがイベントを
 * 消す指定になる）。1つでも入力されていれば入れ子を送り、必須の判定はバックエンドへ委ねる。ここで
 * 「名前が無ければイベントなし」と決めると、会場だけを入れた入力が黙って捨てられる。
 * </p>
 */
const eventOf = (draft: AlbumDraft): AlbumFields['event'] => {
  const event = {
    name: presence(draft['event.name']),
    date: presence(draft['event.date']),
    place: presence(draft['event.place']),
    spaceNumber: presence(draft['event.spaceNumber']),
    note: presence(draft['event.note']),
  };

  return Object.values(event).some((value) => value !== undefined) ? event : undefined;
};

/**
 * 入力値を、作成・更新の要求へ写す。
 *
 * <p>
 * 空文字は項目そのものを送らない形へ落とす。必須の判定は行わない——必須かどうかはバックエンドの
 * 検証が持ち、画面が同じ規則を持つと2箇所へ散る。
 * </p>
 *
 * <p>
 * `coverImageKey` は欄を持たないが、読み込んだ値をそのまま送り返す。更新は全項目置換のため、
 * 送らないとカバー画像を消す指定になる（差し替えの操作は #122 の別スライス）。
 * </p>
 */
export const albumFieldsOf = (draft: AlbumDraft): AlbumFields => ({
  title: presence(draft.title),
  releaseDate: presence(draft.releaseDate),
  artistDisplayName: presence(draft.artistDisplayName),
  artistSortKey: presence(draft.artistSortKey),
  catalogNumber: presence(draft.catalogNumber),
  isdn: presence(draft.isdn),
  coverImageKey: presence(draft.coverImageKey),
  description: presence(draft.description),
  descriptionFormat: presence(draft.descriptionFormat),
  event: eventOf(draft),
});
