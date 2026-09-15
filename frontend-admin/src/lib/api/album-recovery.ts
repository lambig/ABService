import type { AdminAlbumDetail, ApiResult } from './client';

/** 通信断・読めない成功応答・サーバー障害では、変更が反映されていないとは断定できない。 */
export const outcomeUnknown = (result: ApiResult<unknown>): boolean =>
  result.kind === 'failed' &&
  [result.reason !== 'http', (result.status ?? 0) >= 500, result.status === 408].some(Boolean);

/** 再照会は現在値の観測であり、元の要求の成否や影響記事の証明にはならない。 */
export const observedAlbum = (result: ApiResult<AdminAlbumDetail>): string | null =>
  result.kind === 'ok'
    ? `現在の状態：${result.value.publishedAt === null ? '下書き' : '公開'}。`
    : result.kind === 'failed' && result.status === 404
      ? '現在、この作品は見つかりません。'
      : null;
