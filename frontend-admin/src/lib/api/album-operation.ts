import type { AdminSession } from '$lib/credentials';
import { deleteAlbum, unpublishAlbum, type ApiResult } from './client';

export type AlbumOperation = 'delete' | 'unpublish';

/** 成功応答が返した記事と変更内容。事前照会から推測しない。 */
export type AlbumArticleEffect = Readonly<{
  articleId: string;
  title: string;
  description: string;
}>;

const operations = {
  delete: async (
    session: AdminSession,
    albumId: string,
    signal: AbortSignal,
  ): Promise<ApiResult<readonly AlbumArticleEffect[]>> => {
    const result = await deleteAlbum(session, albumId, signal);
    return result.kind === 'ok'
      ? {
          kind: 'ok',
          value: result.value.affectedArticles.map((article) => ({
            articleId: article.articleId,
            title: article.title,
            description: article.unpublished
              ? '作品への参照を失効し、非公開にしました。'
              : '作品への参照を失効しました。',
          })),
        }
      : result;
  },
  unpublish: async (
    session: AdminSession,
    albumId: string,
    signal: AbortSignal,
  ): Promise<ApiResult<readonly AlbumArticleEffect[]>> => {
    const result = await unpublishAlbum(session, albumId, signal);
    return result.kind === 'ok'
      ? {
          kind: 'ok',
          value: result.value.cascadeUnpublishedArticles.map((article) => ({
            articleId: article.articleId,
            title: article.title,
            description: '連動して非公開にしました。',
          })),
        }
      : result;
  },
};

export const runAlbumOperation = (
  operation: AlbumOperation,
  session: AdminSession,
  albumId: string,
  signal: AbortSignal,
): Promise<ApiResult<readonly AlbumArticleEffect[]>> =>
  operations[operation](session, albumId, signal);
