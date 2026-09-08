import { PUBLIC_API_BASE_URL } from 'astro:env/client';

import type { components } from './schema';
import { requestJson } from './http';
import type { ApiResult } from './http';
export type { ApiResult } from './http';

type Schemas = components['schemas'];

/** 管理向けアルバム一覧の1件。下書き（`publishedAt` が null）を含む */
export type AdminAlbum = Schemas['AdminAlbumResponse'];

/** 削除の前提として返る、影響を受ける記事1件 */
export type DeletionAffectedArticle = Schemas['PreconditionAffectedArticle'];

/** 非公開化の前提として返る、連動して非公開になる記事1件 */
export type UnpublicationAffectedArticle = Schemas['CascadeUnpublishedArticle'];

/** 一覧の取得件数。ページ送りの導線は画面を足すときに置く */
const PAGE_SIZE = 50;

const request = <T>(
  method: 'GET' | 'POST' | 'DELETE',
  path: string,
  apiKey: string,
): Promise<ApiResult<T>> =>
  requestJson<T>(`${PUBLIC_API_BASE_URL}${path}`, {
    method,
    headers: { Authorization: `Bearer ${apiKey}` },
  });

/** 下書きを含むアルバムを取得する。 */
export const listAlbums = async (apiKey: string): Promise<ApiResult<readonly AdminAlbum[]>> => {
  const result = await request<Schemas['AdminAlbumListResponse']>(
    'GET',
    `/api/v1/admin/albums?page=0&size=${String(PAGE_SIZE)}`,
    apiKey,
  );

  return result.kind === 'ok' ? { kind: 'ok', value: result.value.items } : result;
};

/**
 * 応答の枝から、その操作の前提だけを取り出す。
 *
 * <p>
 * 前提の照会は操作ごとに枝が分かれた1つの応答（`AlbumPreconditionsResponse`）を返す。問い合わせた
 * 操作の枝が埋まっていないのは契約違反のため、失敗として扱う（空として扱うと、影響が無いことと
 * 契約が破れたことを混ぜる）。
 * </p>
 */
const preconditionsBranch = <T>(
  result: ApiResult<Schemas['AlbumPreconditionsResponse']>,
  branch: (response: Schemas['AlbumPreconditionsResponse']) => T | null,
): ApiResult<T> => {
  const value = result.kind === 'ok' ? branch(result.value) : null;

  return result.kind !== 'ok'
    ? result
    : value === null
      ? { kind: 'failed', message: '管理APIの応答に、問い合わせた操作の前提がありません。' }
      : { kind: 'ok', value };
};

const preconditions = (
  apiKey: string,
  albumId: string,
  operation: 'delete' | 'unpublish',
): Promise<ApiResult<Schemas['AlbumPreconditionsResponse']>> =>
  request<Schemas['AlbumPreconditionsResponse']>(
    'GET',
    `/api/v1/admin/albums/${encodeURIComponent(albumId)}/preconditions?operation=${operation}`,
    apiKey,
  );

/**
 * 削除の前提を問う。返るのは、削除したときに影響を受ける記事。
 *
 * 影響の判定はバックエンドが持つ（`docs/ARCHITECTURE.md`）。画面は返ったものを並べるだけで、
 * 参照元の一覧から「どれが非公開になるか」を組み立て直さない。
 */
export const deletionPreconditions = async (
  apiKey: string,
  albumId: string,
): Promise<ApiResult<readonly DeletionAffectedArticle[]>> =>
  preconditionsBranch(
    await preconditions(apiKey, albumId, 'delete'),
    (response) => response.deletion?.affectedArticles ?? null,
  );

/** 非公開化の前提を問う。返るのは、連動して非公開になる記事。 */
export const unpublicationPreconditions = async (
  apiKey: string,
  albumId: string,
): Promise<ApiResult<readonly UnpublicationAffectedArticle[]>> =>
  preconditionsBranch(
    await preconditions(apiKey, albumId, 'unpublish'),
    (response) => response.unpublication?.articlesBecomingUnpublished ?? null,
  );

/** アルバムを削除する。返るのは、実際に影響を受けた記事。 */
export const deleteAlbum = (
  apiKey: string,
  albumId: string,
): Promise<ApiResult<Schemas['DeleteAlbumResponse']>> =>
  request<Schemas['DeleteAlbumResponse']>(
    'DELETE',
    `/api/v1/albums/${encodeURIComponent(albumId)}`,
    apiKey,
  );

/** アルバムを公開する。 */
export const publishAlbum = (
  apiKey: string,
  albumId: string,
): Promise<ApiResult<Schemas['PublishAlbumResponse']>> =>
  request<Schemas['PublishAlbumResponse']>(
    'POST',
    `/api/v1/albums/${encodeURIComponent(albumId)}/publish`,
    apiKey,
  );

/** アルバムを非公開へ戻す。返るのは、連動して非公開になった記事。 */
export const unpublishAlbum = (
  apiKey: string,
  albumId: string,
): Promise<ApiResult<Schemas['UnpublishAlbumResponse']>> =>
  request<Schemas['UnpublishAlbumResponse']>(
    'POST',
    `/api/v1/albums/${encodeURIComponent(albumId)}/unpublish`,
    apiKey,
  );
