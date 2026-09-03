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

/** 公開向け記事一覧の1件 */
export type PublicArticle = Schemas['PublicArticleResponse'];

/** 公開向け記事詳細 */
export type PublicArticleDetail = Schemas['PublicArticleDetailResponse'];

/** サイトの文言1件 */
export type SiteContent = Schemas['SiteContentResponse'];

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

/** ページ送りの応答。総ページ数から残りを決めるために使う */
interface PagedResponse<T> {
  readonly items: readonly T[];
  readonly totalPages: number;
}

/**
 * ページ送りの応答を全件たぐる。
 *
 * <p>
 * 1ページ目の総ページ数から残りを決め、まとめて取得する。件数が1回あたりの上限を超えても取りこぼさない。
 * </p>
 */
const listAll = async <T>(
  fetchPage: (page: number) => Promise<PagedResponse<T>>,
): Promise<readonly T[]> => {
  const firstPage = await fetchPage(0);
  const remainingPages = await Promise.all(
    Array.from({ length: Math.max(firstPage.totalPages - 1, 0) }, (_unused, index) =>
      fetchPage(index + 1),
    ),
  );
  return [firstPage, ...remainingPages].flatMap((page) => page.items);
};

/*
 * 作品の並びはカタログナンバーの降順で固定する（#197）。並び替えの UI は置かないため、
 * 取得側でキーを決める。降順はバックエンドのキーごとの既定に一致する。
 */
const fetchAlbumPage = (page: number): Promise<Schemas['PublicAlbumListResponse']> =>
  fetchPublic(`/api/v1/albums?page=${String(page)}&size=${String(PAGE_SIZE)}&sort=catalogNumber`);

/** 公開中のアルバムを全件取得する（カタログナンバーの降順）。 */
export const listAlbums = (): Promise<readonly PublicAlbum[]> => listAll(fetchAlbumPage);

/** 公開中のアルバム詳細を取得する。 */
export const getAlbum = (albumId: string): Promise<PublicAlbumDetail> =>
  fetchPublic(`/api/v1/albums/${albumId}`);

/*
 * 記事の並びは公開日の降順で固定する（#197）。日付を軸にした導線とタグの絞り込みは #210。
 */
const fetchArticlePage = (page: number): Promise<Schemas['PublicArticleListResponse']> =>
  fetchPublic(`/api/v1/articles?page=${String(page)}&size=${String(PAGE_SIZE)}&sort=publishedAt`);

/** 公開中の記事を全件取得する（公開日の降順）。 */
export const listArticles = (): Promise<readonly PublicArticle[]> => listAll(fetchArticlePage);

/** 公開中の記事詳細を取得する。 */
export const getArticle = (articleId: string): Promise<PublicArticleDetail> =>
  fetchPublic(`/api/v1/articles/${articleId}`);

/**
 * サイトの文言を全件取得する（キーの昇順）。
 *
 * <p>
 * ページネーションを持たない。未登録のキーは応答に現れないため、利用側は該当するキーが無ければその区画を
 * 出さない（#230）。
 * </p>
 */
export const listSiteContents = async (): Promise<readonly SiteContent[]> => {
  const response = await fetchPublic<Schemas['SiteContentListResponse']>('/api/v1/site-contents');
  return response.items;
};
