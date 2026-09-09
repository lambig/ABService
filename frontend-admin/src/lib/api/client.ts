import { PUBLIC_API_BASE_URL } from 'astro:env/client';

import type { components } from './schema';
import { requestEmpty, requestJson } from './http';
import type { ApiResult } from './http';
export type { ApiResult } from './http';

type Schemas = components['schemas'];

/** 管理向けアルバム一覧の1件。下書き（`publishedAt` が null）を含む */
export type AdminAlbum = Schemas['AdminAlbumResponse'];

/** 管理向けアルバム詳細。編集の初期値として読む */
export type AdminAlbumDetail = Schemas['AdminAlbumDetailResponse'];

/**
 * 作成・更新が送る項目。
 *
 * <p>
 * 作成（`CreateAlbumRequest`）と更新（`UpdateAlbumRequest`）は同じ項目を持つ。画面の入力も同じで、
 * 違うのは経路と、更新が全項目置換であること。片方の型だけを使うと、生成した型が食い違ったときに
 * 検査が通ってしまうため、両方を満たす形として宣言する。
 * </p>
 */
export type AlbumFields = Schemas['UpdateAlbumRequest'] & Schemas['CreateAlbumRequest'];

/** 削除の前提として返る、影響を受ける記事1件 */
export type DeletionAffectedArticle = Schemas['PreconditionAffectedArticle'];

/** 非公開化の前提として返る、連動して非公開になる記事1件 */
export type UnpublicationAffectedArticle = Schemas['CascadeUnpublishedArticle'];

/**
 * 一覧1ページの件数。
 *
 * <p>
 * 記事の一覧はこの単位でページを送る。作品の一覧はページ送りの導線をまだ持たず、この件数までしか
 * 辿れない（#122）。
 * </p>
 */
const PAGE_SIZE = 50;

/** 本体を持つ要求だけが宣言する媒体型。持たない要求へ付けると、送っていない形を宣言することになる */
const contentTypeOf = (body: unknown): Readonly<Record<string, string>> =>
  body === undefined ? {} : { 'Content-Type': 'application/json' };

const bodyOf = (body: unknown): RequestInit =>
  body === undefined ? {} : { body: JSON.stringify(body) };

const request = <T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  apiKey: string,
  body?: unknown,
): Promise<ApiResult<T>> =>
  requestJson<T>(`${PUBLIC_API_BASE_URL}${path}`, {
    method,
    headers: { Authorization: `Bearer ${apiKey}`, ...contentTypeOf(body) },
    ...bodyOf(body),
  });

/**
 * 本体を返さない操作の経路。
 *
 * <p>
 * 204 をそのまま成功として扱い、JSON の読み取りを求めない（#288）。本体を返す操作と同じ経路に
 * まとめると、本体の無い応答を型 `T` に偽装することになる。
 * </p>
 */
const requestNoContent = (
  method: 'DELETE',
  path: string,
  apiKey: string,
): Promise<ApiResult<void>> =>
  requestEmpty(`${PUBLIC_API_BASE_URL}${path}`, {
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
 * 編集する作品を1件引く（下書きを含む）。
 *
 * 公開向けの詳細ではなく管理向けを引く。編集の対象は下書きも含み、公開向けには出ないため。
 */
export const getAlbum = (apiKey: string, albumId: string): Promise<ApiResult<AdminAlbumDetail>> =>
  request<AdminAlbumDetail>('GET', `/api/v1/admin/albums/${encodeURIComponent(albumId)}`, apiKey);

/** 作品を作る（下書きとして作られる）。 */
export const createAlbum = (
  apiKey: string,
  fields: AlbumFields,
): Promise<ApiResult<Schemas['CreateAlbumResponse']>> =>
  request<Schemas['CreateAlbumResponse']>('POST', '/api/v1/albums', apiKey, fields);

/**
 * 作品を更新する（PUT風の全項目置換。トラックと外部音源は対象外）。
 *
 * <p>
 * 編集を始めた時点の世代（`expectedRevision`）を必ず送る。全項目置換のため、これを持たない更新は編集の
 * 間に入った別の保存を消す。世代が古ければ 409 が返る（#287）。
 * </p>
 */
export const updateAlbum = (
  apiKey: string,
  albumId: string,
  fields: AlbumFields,
  expectedRevision: number,
): Promise<ApiResult<Schemas['UpdateAlbumResponse']>> =>
  request<Schemas['UpdateAlbumResponse']>(
    'PUT',
    `/api/v1/albums/${encodeURIComponent(albumId)}`,
    apiKey,
    { ...fields, expectedRevision },
  );

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

/** 管理向け記事一覧の1件。下書き（`publicFlag` が false）を含む */
export type AdminArticle = Schemas['AdminArticleResponse'];

/**
 * 管理向け記事一覧の1ページ。
 *
 * <p>
 * 応答が返したページ情報（`page` / `size` / `totalElements` / `totalPages`）を落とさず持つ。件数だけを
 * 取り出すと、1ページに収まらない記事へ画面から辿り着けなくなる。
 * </p>
 */
export type AdminArticlePage = Schemas['AdminArticleListResponse'];

/**
 * 記事一覧の並び順。
 *
 * <p>
 * 業務上の更新日時で並べる（`ArticleSortKey` が管理向けに許すキー）。監査列は記録のための列であり、
 * 編集の作業順を表さない。
 * </p>
 */
const ARTICLE_LIST_SORT = 'updatedAtBusiness';

/**
 * 下書きを含む記事の1ページを取得する。
 *
 * @param apiKey 管理APIの鍵
 * @param page 0 始まりのページ番号
 */
export const listArticles = (apiKey: string, page: number): Promise<ApiResult<AdminArticlePage>> =>
  request<AdminArticlePage>(
    'GET',
    `/api/v1/admin/articles?page=${String(page)}&size=${String(PAGE_SIZE)}&sort=${ARTICLE_LIST_SORT}`,
    apiKey,
  );

/** 記事を公開する。 */
export const publishArticle = (
  apiKey: string,
  articleId: string,
): Promise<ApiResult<Schemas['PublishArticleResponse']>> =>
  request<Schemas['PublishArticleResponse']>(
    'POST',
    `/api/v1/articles/${encodeURIComponent(articleId)}/publish`,
    apiKey,
  );

/** 記事を非公開へ戻す。 */
export const unpublishArticle = (
  apiKey: string,
  articleId: string,
): Promise<ApiResult<Schemas['UnpublishArticleResponse']>> =>
  request<Schemas['UnpublishArticleResponse']>(
    'POST',
    `/api/v1/articles/${encodeURIComponent(articleId)}/unpublish`,
    apiKey,
  );

/**
 * 記事を削除する。
 *
 * <p>
 * 応答は 204 で本体を持たない。作品の削除は影響を受けた記事を返すが、記事を指す集約は無いため
 * 返すものが無い（DECISIONS 27）。
 * </p>
 */
export const deleteArticle = (apiKey: string, articleId: string): Promise<ApiResult<void>> =>
  requestNoContent('DELETE', `/api/v1/articles/${encodeURIComponent(articleId)}`, apiKey);
