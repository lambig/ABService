import { stack } from './config.ts';

/**
 * 管理API経由でデータを投入する。
 *
 * SQL のフィクスチャを作らないのは、投入経路そのものも同時に検証するため（#164）。テストが必要とする
 * 形だけを持ち、網羅はしない。
 */

/** 作る作品の指定。省略した項目は API の既定に従う */
export interface AlbumSeed {
  readonly title: string;
  readonly releaseDate: string;
  readonly artistDisplayName: string;
  readonly artistSortKey: string;
  readonly catalogNumber?: string;
  readonly isdn?: string;
  readonly description?: string;
  readonly descriptionFormat?: 'MARKDOWN' | 'PLAIN_TEXT';
  readonly event?: {
    readonly name: string;
    readonly date?: string;
    readonly place?: string;
    readonly spaceNumber?: string;
    readonly note?: string;
  };
  readonly tracks?: readonly TrackSeed[];
  readonly externalAudioUrls?: readonly string[];
}

/** 作るトラックの指定 */
export interface TrackSeed {
  readonly trackNo: number;
  readonly title: string;
  readonly artistDisplayName?: string;
  readonly tunes?: readonly TuneSeed[];
}

/** トラック内のチューン構成 */
export interface TuneSeed {
  readonly seq: number;
  readonly tuneTitle: string;
  readonly composerCreditOverride?: string;
  readonly arrangerCreditOverride?: string;
}

const adminHeaders = {
  Authorization: `Bearer ${stack.adminApiKey}`,
  'Content-Type': 'application/json',
} as const;

const postAdmin = async (path: string, body: unknown): Promise<unknown> => {
  const response = await fetch(`${stack.backendBaseUrl}${path}`, {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify(body),
  });
  const text = await response.text();
  return response.ok
    ? (JSON.parse(text) as unknown)
    : Promise.reject(
        new Error(`POST ${path} が失敗しました（HTTP ${String(response.status)}）: ${text}`),
      );
};

const albumIdOf = (created: unknown): string => {
  const albumId = (created as { albumId?: unknown }).albumId;
  return typeof albumId === 'string'
    ? albumId
    : (() => {
        throw new Error(`作品の作成応答に albumId がありません: ${JSON.stringify(created)}`);
      })();
};

/**
 * 作品を作り、トラックと外部音源を付けて公開する。
 *
 * @returns 作った作品のドメインID
 */
export const seedPublishedAlbum = async (album: AlbumSeed): Promise<string> => {
  const created = await postAdmin('/api/v1/albums/with-tracks', {
    title: album.title,
    releaseDate: album.releaseDate,
    artistDisplayName: album.artistDisplayName,
    artistSortKey: album.artistSortKey,
    catalogNumber: album.catalogNumber,
    isdn: album.isdn,
    description: album.description,
    descriptionFormat: album.descriptionFormat,
    event: album.event,
    tracks: (album.tracks ?? []).map((track) => ({
      trackNo: track.trackNo,
      title: track.title,
      artistDisplayName: track.artistDisplayName,
      tunes: (track.tunes ?? []).map((tune) => ({
        seq: tune.seq,
        tuneTitle: tune.tuneTitle,
        composerCreditOverride: tune.composerCreditOverride,
        arrangerCreditOverride: tune.arrangerCreditOverride,
      })),
    })),
  });

  const albumId = albumIdOf(created);

  /*
   * SEQUENTIAL-ORDER: 外部音源の表示順は末尾採番のため、並列に投げると順序が実行ごとに変わる。
   * 指定した並びをそのまま再現するために1件ずつ送る。
   */
  for (const url of album.externalAudioUrls ?? []) {
    await postAdmin(`/api/v1/albums/${albumId}/external-audios`, { url });
  }

  await postAdmin(`/api/v1/albums/${albumId}/publish`, {});

  return albumId;
};
