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
 * 管理向け記事詳細。編集の初期値として読む。
 *
 * <p>
 * 種別ごとのサブタイプの合併で、`ALBUM` だけが作品への参照を持つ。編集が扱うのはどの種別にも共通の
 * 項目だけのため、この段では枝を分けない（参照の操作は #309 の別スライス）。
 * </p>
 */
export type AdminArticleDetail = Schemas['AdminArticleDetailResponse'];

/**
 * 作成・更新が送る項目。
 *
 * <p>
 * 作成（`CreateArticleRequest`）と更新（`UpdateArticleRequest`）は同じ項目を持ち、違うのは経路と、
 * 更新が全項目置換で編集開始時点の世代を伴うこと。片方の型だけを使うと、生成した型が食い違ったときに
 * 検査が通ってしまうため、両方を満たす形として宣言する。
 * </p>
 */
export type ArticleFields = Schemas['UpdateArticleRequest'] & Schemas['CreateArticleRequest'];

/** 記事タグ1件。名前で同定し、外すときだけ `tagId` を使う（DECISIONS 23） */
export type AdminArticleTag = Schemas['AdminArticleTagResponse'];

/**
 * 記事から参照する作品を探した結果。
 *
 * <p>
 * 下書きを含む（管理向けの一覧を引くため）。参照できるかどうかの判定はバックエンドが持ち、画面は
 * 返った候補を並べるだけ。
 * </p>
 */
export type AlbumCandidate = AdminAlbum;

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

/**
 * 編集する記事を1件引く（下書きを含む）。
 *
 * 公開向けの詳細ではなく管理向けを引く。編集の対象は下書きも含み、公開向けには出ないため。
 */
export const getArticle = (
  apiKey: string,
  articleId: string,
): Promise<ApiResult<AdminArticleDetail>> =>
  request<AdminArticleDetail>(
    'GET',
    `/api/v1/admin/articles/${encodeURIComponent(articleId)}`,
    apiKey,
  );

/**
 * 記事を作る（下書きとして作られる）。
 *
 * <p>
 * 応答は世代を返さない。作った記事をそのまま編集し続けるには、作成の後に管理向け詳細を引き直して
 * 世代を得る（世代を推測すると、最初の保存が別の編集を消しかねない）。
 * </p>
 */
export const createArticle = (
  apiKey: string,
  fields: ArticleFields,
): Promise<ApiResult<Schemas['CreateArticleResponse']>> =>
  request<Schemas['CreateArticleResponse']>('POST', '/api/v1/articles', apiKey, fields);

/**
 * 記事を更新する（PUT風の全項目置換。公開状態とタグは対象外）。
 *
 * <p>
 * 編集を始めた時点の世代（`expectedRevision`）を必ず送る。全項目置換のため、これを持たない更新は編集の
 * 間に入った別の保存を消す。世代が古ければ 409 が返る（#287）。応答は保存後の世代を返すため、画面に
 * 留まったまま続けて編集できる。
 * </p>
 */
export const updateArticle = (
  apiKey: string,
  articleId: string,
  fields: ArticleFields,
  expectedRevision: number,
): Promise<ApiResult<Schemas['UpdateArticleResponse']>> =>
  request<Schemas['UpdateArticleResponse']>(
    'PUT',
    `/api/v1/articles/${encodeURIComponent(articleId)}`,
    apiKey,
    { ...fields, expectedRevision },
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

/**
 * 付けられるタグの一覧。
 *
 * <p>
 * 既にある名前を選ばせるために引く。画面が候補を持たないと、同じ意味のタグが表記違いで増える。
 * </p>
 */
export const listArticleTags = async (
  apiKey: string,
): Promise<ApiResult<readonly AdminArticleTag[]>> => {
  const result = await request<Schemas['AdminArticleTagListResponse']>(
    'GET',
    '/api/v1/admin/article-tags',
    apiKey,
  );

  return result.kind === 'ok' ? { kind: 'ok', value: result.value.items } : result;
};

/**
 * 記事にタグを付ける。
 *
 * <p>
 * 送るのは**名前**で、同じ名前のタグが無ければ作られる（DECISIONS 23）。画面は同名かどうかを判定
 * しない——判定の規則はバックエンドが持ち、写すと2箇所へ散る。
 * </p>
 */
export const addArticleTag = (
  apiKey: string,
  articleId: string,
  name: string,
): Promise<ApiResult<Schemas['AddArticleTagResponse']>> =>
  request<Schemas['AddArticleTagResponse']>(
    'POST',
    `/api/v1/articles/${encodeURIComponent(articleId)}/tags`,
    apiKey,
    { name },
  );

/**
 * 記事からタグを外す。
 *
 * <p>
 * 外す対象は `tagId` で指す（付けるときは名前だが、外すのは既に付いている1件のため）。応答は 204 で
 * 本体を持たない。
 * </p>
 */
export const removeArticleTag = (
  apiKey: string,
  articleId: string,
  tagId: string,
): Promise<ApiResult<void>> =>
  requestNoContent(
    'DELETE',
    `/api/v1/articles/${encodeURIComponent(articleId)}/tags/${encodeURIComponent(tagId)}`,
    apiKey,
  );

/** 検索の取得件数。選ぶための候補で、全件を辿るための一覧ではない */
const ALBUM_SEARCH_SIZE = 20;

/**
 * 参照する作品を探す。
 *
 * <p>
 * タイトルとカタログナンバーの両方を同じ語で問い合わせる経路は無いため、入力された語をどちらの絞り込みに
 * 渡すかは呼び出し側が決める（#208）。絞り込みは部分一致で、判定はバックエンドが持つ。
 * </p>
 *
 * @param apiKey 管理APIの鍵
 * @param by 絞り込む項目
 * @param keyword 入力された語
 */
export const searchAlbums = async (
  apiKey: string,
  by: 'title' | 'catalogNumber',
  keyword: string,
): Promise<ApiResult<readonly AlbumCandidate[]>> => {
  const result = await request<Schemas['AdminAlbumListResponse']>(
    'GET',
    `/api/v1/admin/albums?page=0&size=${String(ALBUM_SEARCH_SIZE)}&${by}=${encodeURIComponent(keyword)}`,
    apiKey,
  );

  return result.kind === 'ok' ? { kind: 'ok', value: result.value.items } : result;
};

/**
 * 記事が参照する作品を設定する。
 *
 * <p>
 * 参照を持てるのは `ALBUM` 種別だけで、未存在・非公開などの判定はバックエンドが返す（DECISIONS 21）。
 * 画面は結果を扱うだけで、参照できるかどうかを先に判定しない。
 * </p>
 *
 * <p>
 * 紐付けは記事の世代を進めるため、編集を始めた時点の世代（`expectedRevision`）を送る。世代が古ければ
 * 409 が返る。応答は紐付け後の世代を返すため、GETで取り直さずそのまま次の条件にできる（#323）。
 * </p>
 */
export const setArticleAlbum = (
  apiKey: string,
  articleId: string,
  albumId: string,
  expectedRevision: number,
): Promise<ApiResult<Schemas['SetArticleAlbumResponse']>> =>
  request<Schemas['SetArticleAlbumResponse']>(
    'PUT',
    `/api/v1/articles/${encodeURIComponent(articleId)}/album`,
    apiKey,
    { albumId, expectedRevision },
  );

/**
 * 記事から作品への参照を外す。
 *
 * <p>
 * 解除も記事の世代を進めるため、紐付けと同じ `expectedRevision` 契約を適用する。応答は解除後の世代を
 * 返すため、GETで取り直さずそのまま次の条件にできる（#323）。
 * </p>
 */
export const removeArticleAlbum = (
  apiKey: string,
  articleId: string,
  expectedRevision: number,
): Promise<ApiResult<Schemas['RemoveArticleAlbumResponse']>> =>
  request<Schemas['RemoveArticleAlbumResponse']>(
    'DELETE',
    `/api/v1/articles/${encodeURIComponent(articleId)}/album?expectedRevision=${String(expectedRevision)}`,
    apiKey,
  );
