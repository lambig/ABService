import { API_BASE_URL } from 'astro:env/server';

import type { components } from './schema';

type Schemas = components['schemas'];

/** 公開向けアルバム一覧の1件 */
export type PublicAlbum = Schemas['PublicAlbumResponse'];

/** 公開向けアルバム詳細 */
export type PublicAlbumDetail = Schemas['PublicAlbumDetailResponse'];

/** アルバムの曲目 */
export type PublicTrack = Schemas['PublicTrackResponse'];

/** アルバムの外部音源 */
export type PublicExternalAudio = Schemas['PublicExternalAudioResponse'];

/** 一覧の1回あたりの取得件数。バックエンドが受け付ける上限 */
const PAGE_SIZE = 100;

/**
 * 公開 Query API から取得する。
 *
 * <p>
 * 呼ばれるのはビルド時だけで、ブラウザからは呼ばれない（静的出力）。取得に失敗したらビルドを失敗させる。
 * 欠けたページで公開サイトを作るより、作らない方がよい。
 * </p>
 */
const fetchPublic = async <T>(path: string): Promise<T> => {
  const response = await fetch(`${API_BASE_URL}${path}`);
  const body: unknown = response.ok
    ? await response.json()
    : await Promise.reject(
        new Error(`GET ${path} が失敗しました（HTTP ${String(response.status)}）`),
      );
  return body as T;
};

/*
 * 作品の並びはカタログナンバーの降順で固定する（#197）。並び替えの UI は置かないため、
 * 取得側でキーを決める。降順はバックエンドのキーごとの既定に一致する。
 */
const fetchAlbumPage = (page: number): Promise<Schemas['PublicAlbumListResponse']> =>
  fetchPublic(`/api/v1/albums?page=${String(page)}&size=${String(PAGE_SIZE)}&sort=catalogNumber`);

/**
 * 公開中のアルバムを全件取得する（カタログナンバーの降順）。
 *
 * <p>
 * 1ページ目の総ページ数から残りを決め、まとめて取得する。件数が上限を超えても取りこぼさない。
 * </p>
 */
export const listAlbums = async (): Promise<readonly PublicAlbum[]> => {
  const firstPage = await fetchAlbumPage(0);
  const remainingPages = await Promise.all(
    Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_unused, index) =>
      fetchAlbumPage(index + 1),
    ),
  );
  return [firstPage, ...remainingPages].flatMap((page) => page.items);
};

/** 公開中のアルバム詳細を取得する。 */
export const getAlbum = (albumId: string): Promise<PublicAlbumDetail> =>
  fetchPublic(`/api/v1/albums/${albumId}`);
