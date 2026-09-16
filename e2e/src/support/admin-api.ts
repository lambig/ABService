import { adminApi, toUpdateAlbumRequest, toUpdateArticleRequest } from 'abservice-admin-api';
import { stack } from './config.ts';

/**
 * E2E のスタックに結び付いた管理APIクライアント。
 *
 * <p>
 * 操作の実体は `packages/admin-api` が持ち、初期データのローダ（#373）と同じ経路を通る。ここが持つのは
 * スタックへの結び付けと、シナリオのための言い回しだけ。
 * </p>
 */
const api = adminApi({ baseUrl: stack.backendBaseUrl, apiKey: stack.adminApiKey });

export type {
  AdminAlbum,
  AdminArticle,
  AlbumSeed,
  ArticleSeed,
  AssetSeed,
  SiteContentSeed,
  TrackSeed,
  TuneSeed,
} from 'abservice-admin-api';

export const {
  seedAsset,
  seedDraftAlbum,
  seedPublishedAlbum,
  setAlbumCoverImage,
  ensureAlbumCoverImage,
  publishAlbum,
  unpublishAlbum,
  deleteAlbum,
  fetchAdminAlbumPage,
  findAlbumByCatalogNumber,
  findAlbumsByCatalogNumberPrefix,
  seedDraftArticle,
  publishArticle,
  deleteArticle,
  findArticleByTitle,
  findArticlesByTitlePrefix,
  countArticles,
  upsertSiteContent,
} = api;

/**
 * 別のタブが保存した状態を作る（タイトルだけを変えて全項目置換する）。
 *
 * <p>
 * 更新は編集を始めた時点の世代（`expectedRevision`）を要求するため、詳細を読んでから送る（#287）。画面が
 * 同じ作品を開いたまま古い世代で保存しようとしたときに、競合として拒まれることを見るために使う。
 * </p>
 *
 * @param albumId
 *            対象の作品のドメインID
 * @param title
 *            置き換え後のタイトル
 */
export const renameAlbumOutsideTheScreen = async (
  albumId: string,
  title: string,
): Promise<void> => {
  const detail = await api.getAdminAlbumDetail(albumId);
  await api.updateAlbum(albumId, { ...toUpdateAlbumRequest(detail), title });
};

/**
 * 別のタブが保存した状態を作る（タイトルだけを変えて全項目置換する）。
 *
 * @param articleId
 *            対象の記事のドメインID
 * @param title
 *            置き換え後のタイトル
 */
export const renameArticleOutsideTheScreen = async (
  articleId: string,
  title: string,
): Promise<void> => {
  const detail = await api.getAdminArticleDetail(articleId);
  await api.updateArticle(articleId, { ...toUpdateArticleRequest(detail), title });
};
