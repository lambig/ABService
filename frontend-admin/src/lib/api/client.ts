import { PUBLIC_API_BASE_URL } from 'astro:env/client';

import type { components } from './schema';

type Schemas = components['schemas'];

/** 管理向けアルバム一覧の1件。下書き（`publishedAt` が null）を含む */
export type AdminAlbum = Schemas['AdminAlbumResponse'];

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
 * 管理APIを叩く。
 *
 * <p>
 * 公開サイトと違い、呼ばれるのは**ブラウザ**（静的出力のため、組み立ての時点では下書きを含む状態を
 * 持てない）。したがって到達できないこと自体が起こり得るため、例外ではなく結果の値で返す。
 * </p>
 */
const request = async <T>(path: string, apiKey: string): Promise<ApiResult<T>> => {
  const response = await fetch(`${PUBLIC_API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${apiKey}` },
  }).catch(() => null);

  return response === null
    ? { kind: 'failed', message: '管理APIへ接続できません。' }
    : AUTH_FAILURE_STATUSES.includes(response.status)
      ? { kind: 'unauthorized' }
      : response.ok
        ? { kind: 'ok', value: (await response.json()) as T }
        : { kind: 'failed', message: `管理APIが失敗しました（HTTP ${String(response.status)}）。` };
};

/** 下書きを含むアルバムを取得する。 */
export const listAlbums = async (apiKey: string): Promise<ApiResult<readonly AdminAlbum[]>> => {
  const result = await request<Schemas['AdminAlbumListResponse']>(
    `/api/v1/admin/albums?page=0&size=${String(PAGE_SIZE)}`,
    apiKey,
  );

  return result.kind === 'ok' ? { kind: 'ok', value: result.value.items } : result;
};
