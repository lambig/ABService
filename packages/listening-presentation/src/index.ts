import { getPlaybackItems } from 'abservice-installation';
import type { InstallationManifest, PlaybackItem } from 'abservice-installation';
import { renderMarkup } from 'abservice-markup';

/**
 * 作品の収録曲。説明のための一覧で、再生できる項目ではない。
 * trackNo は canonical な曲番号で、連番とは限らない（欠番もありうる）。配列の位置から推測しない。
 * 曲番号を持たない schema v1 の Manifest では省く。
 */
export type ListeningTrack = Readonly<{
  trackId: string;
  trackNo?: number;
  title: string;
}>;

/**
 * 試聴画面が表示する作品の事実。値の無い項目はキーごと省き、placeholder を事実として持たない。
 * layout・scene・effect は持たない（見せ方は presentation の側が決める）。
 * - artwork: asset store が検証した実体。保存されていなければ省く
 * - descriptionHtml: サニタイズ済みの HTML。画像は除き、リンクは文字だけにする
 */
export type ListeningAlbum = Readonly<{
  albumId: string;
  title: string;
  artistDisplayName?: string;
  releaseDate?: string;
  catalogNumber?: string;
  originalWorkNote?: string;
  descriptionHtml?: string;
  artwork?: Blob;
  tracks: readonly ListeningTrack[];
}>;

/**
 * 試聴画面へ渡す表示データ。作品の一覧と、選んで再生できる項目を分けて持つ。
 * 収録曲は作品の説明で、音源を持つ再生項目だけが選べる。
 */
export type PresentationData = Readonly<{
  albums: readonly ListeningAlbum[];
  playbackItems: readonly PlaybackItem[];
}>;

type Album = InstallationManifest['albums'][number];
type Presented = Partial<
  Record<
    | 'artistDisplayName'
    | 'releaseDate'
    | 'catalogNumber'
    | 'originalWorkNote'
    | 'description'
    | 'descriptionFormat',
    string
  >
>;

const escape = (text: string): string =>
  text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
/* Plain text keeps its paragraphs and line breaks; nothing in it is interpreted as markup. */
const plainText = (text: string): string =>
  text
    .split(/\n{2,}/u)
    .filter((paragraph) => paragraph.trim() !== '')
    .map((paragraph) => `<p>${escape(paragraph).replaceAll('\n', '<br>')}</p>`)
    .join('');
const describe = (
  description: string | undefined,
  format: string | undefined,
): string | undefined => {
  const html =
    description === undefined
      ? ''
      : format === 'MARKDOWN'
        ? renderMarkup(description, { offline: true })
        : plainText(description);
  return html.trim() === '' ? undefined : html;
};
/* Only the facts present in the schema are copied; an absent or empty value omits the key. */
const facts = (album: Presented) =>
  Object.fromEntries(
    (
      [
        'artistDisplayName',
        'releaseDate',
        'catalogNumber',
        'originalWorkNote',
      ] as const
    ).flatMap((key) => {
      const value = album[key]?.trim() ?? '';
      return value === '' ? [] : [[key, album[key]]];
    }),
  ) as Pick<
    ListeningAlbum,
    'artistDisplayName' | 'releaseDate' | 'catalogNumber' | 'originalWorkNote'
  >;
const present = (
  album: Album,
  artworks: ReadonlyMap<string, Blob>,
): ListeningAlbum => {
  const presented = album as Album & Presented;
  const descriptionHtml = describe(
    presented.description,
    presented.descriptionFormat,
  );
  const artwork =
    album.artworkAssetId === undefined
      ? undefined
      : artworks.get(album.artworkAssetId);
  return Object.freeze({
    albumId: album.albumId,
    title: album.title,
    ...facts(presented),
    ...(descriptionHtml === undefined ? {} : { descriptionHtml }),
    ...(artwork === undefined ? {} : { artwork }),
    tracks: Object.freeze(
      album.tracks.map((track) =>
        Object.freeze({
          trackId: track.trackId,
          ...('trackNo' in track ? { trackNo: track.trackNo } : {}),
          title: track.title,
        }),
      ),
    ),
  });
};

/** 表示に使う artwork の assetId。呼び出し側はこれを asset store から読み、検証済みの実体だけを渡す。 */
export const artworkAssetIds = (
  manifest: InstallationManifest,
): readonly string[] => [
  ...new Set(
    manifest.albums.flatMap((album) =>
      album.artworkAssetId === undefined ? [] : [album.artworkAssetId],
    ),
  ),
];

/**
 * 検証済みの Manifest と artwork の実体から表示データを組む。I/O と DOM に依存しない純粋な関数。
 * 渡されなかった artwork は保存されていないものとして省き、見せ方の側が欠損時の構成を選ぶ。
 */
export const toPresentationData = (
  manifest: InstallationManifest,
  artworks: ReadonlyMap<string, Blob>,
): PresentationData =>
  Object.freeze({
    albums: Object.freeze(
      manifest.albums.map((album) => present(album, artworks)),
    ),
    playbackItems: getPlaybackItems(manifest),
  });
