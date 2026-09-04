import { PUBLIC_API_BASE_URL } from 'astro:env/client';

import type { components } from './schema';

type Schemas = components['schemas'];

/** 管理向けアルバム一覧の1件。下書き（`publishedAt` が null）を含む */
export type AdminAlbum = Schemas['AdminAlbumResponse'];

/** 削除の前提として返る、影響を受ける記事1件 */
export type DeletionAffectedArticle = Schemas['PreconditionAffectedArticle'];

/** 非公開化の前提として返る、連動して非公開になる記事1件 */
export type UnpublicationAffectedArticle = Schemas['CascadeUnpublishedArticle'];

/**
 * 管理APIの呼び出し結果。
 *
 * 鍵が受け付けられなかった場合を、それ以外の失敗と別の枝で表す。画面が出す文言と次の操作
 * （鍵を入れ直す／やり直す）が変わるため、呼び出し側に状態コードを解釈させない。
 */
export type ApiResult<T> =
  | { readonly kind: 'ok'; readonly value: T }
  | { readonly kind: 'unauthorized' }
  | { readonly kind: 'failed'; readonly message: string };

/** 一覧の取得件数。ページ送りの導線は画面を足すときに置く */
const PAGE_SIZE = 50;

/** 鍵が無い・誤っている場合の応答。401 は未提示または不正、403 はロール不足 */
const AUTH_FAILURE_STATUSES = [401, 403];

/**
 * 本文を読めなかったことを表す印。
 *
 * 応答が空・途中で切れた・JSON でない場合に立てる。`null` を使えないのは、それ自体が正しい本文で
 * ありうるため（読めなかったことと、読めた結果が null であることを混ぜない）。
 */
const UNREADABLE = Symbol('unreadable');

const readBody = async <T>(response: Response): Promise<ApiResult<T>> => {
  const body: unknown = await (response.json() as Promise<unknown>).catch(() => UNREADABLE);

  return body === UNREADABLE
    ? { kind: 'failed', message: '管理APIの応答を読み取れません。' }
    : { kind: 'ok', value: body as T };
};

/**
 * 管理APIを叩く。
 *
 * <p>
 * 公開サイトと違い、呼ばれるのは**ブラウザ**（静的出力のため、組み立ての時点では下書きを含む状態を
 * 持てない）。したがって到達できないこと自体が起こり得るため、例外ではなく結果の値で返す。
 * </p>
 *
 * <p>
 * FOLD-EVERY-FAILURE: 畳むのは接続だけでなく、本文の読み取りまでを含む往復の全体。ここから例外が
 * 抜けると呼び出し側の状態が読み込み中のまま止まり、画面が復帰できなくなる。
 * </p>
 */
const request = async <T>(
  method: 'GET' | 'POST' | 'DELETE',
  path: string,
  apiKey: string,
): Promise<ApiResult<T>> => {
  const response = await fetch(`${PUBLIC_API_BASE_URL}${path}`, {
    method,
    headers: { Authorization: `Bearer ${apiKey}` },
  }).catch(() => null);

  return response === null
    ? { kind: 'failed', message: '管理APIへ接続できません。' }
    : AUTH_FAILURE_STATUSES.includes(response.status)
      ? { kind: 'unauthorized' }
      : response.ok
        ? readBody<T>(response)
        : { kind: 'failed', message: `管理APIが失敗しました（HTTP ${String(response.status)}）。` };
};

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
