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

const getAdmin = async (path: string): Promise<unknown> => {
  const response = await fetch(`${stack.backendBaseUrl}${path}`, { headers: adminHeaders });
  const text = await response.text();
  return response.ok
    ? (JSON.parse(text) as unknown)
    : Promise.reject(
        new Error(`GET ${path} が失敗しました（HTTP ${String(response.status)}）: ${text}`),
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
 * 作品を作り、トラックと外部音源を付ける（下書きのまま）。
 *
 * @returns 作った作品のドメインID
 */
export const seedDraftAlbum = async (album: AlbumSeed): Promise<string> => {
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

  return albumId;
};

/**
 * 下書きの作品を公開する。
 *
 * @param albumId
 *            公開する作品のドメインID
 */
export const publishAlbum = async (albumId: string): Promise<void> => {
  await postAdmin(`/api/v1/albums/${albumId}/publish`, {});
};

/**
 * 公開中の作品を下書きへ戻す。
 *
 * @param albumId
 *            下書きへ戻す作品のドメインID
 */
export const unpublishAlbum = async (albumId: string): Promise<void> => {
  await postAdmin(`/api/v1/albums/${albumId}/unpublish`, {});
};

/**
 * 作品を作り、トラックと外部音源を付けて公開する。
 *
 * @returns 作った作品のドメインID
 */
export const seedPublishedAlbum = async (album: AlbumSeed): Promise<string> => {
  const albumId = await seedDraftAlbum(album);
  await publishAlbum(albumId);
  return albumId;
};

/** 管理向け一覧の1件。同定と公開状態の確認に使う項目だけを持つ */
export interface AdminAlbum {
  readonly albumId: string;
  readonly catalogNumber: string | null;
  /** 公開日時。下書きは null */
  readonly publishedAt: string | null;
}

/**
 * カタログナンバーで作品を引く（下書きを含む）。
 *
 * <p>
 * 公開の一覧には下書きが出ないため、管理APIを通す。絞り込みは部分一致のため、完全一致で選び直す。
 * </p>
 *
 * @param catalogNumber
 *            同定に使うカタログナンバー
 * @returns 見つかった作品。無ければ undefined
 */
export const findAlbumByCatalogNumber = async (
  catalogNumber: string,
): Promise<AdminAlbum | undefined> => {
  const body = await getAdmin(
    `/api/v1/admin/albums?size=100&catalogNumber=${encodeURIComponent(catalogNumber)}`,
  );
  const { items } = body as { items: readonly AdminAlbum[] };
  return items.find((item) => item.catalogNumber === catalogNumber);
};
