import { render, screen, waitFor, within } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { authenticate, logout } from '$lib/credentials';
import { requestEmpty, requestJson, type ApiResult } from '$lib/api/http';
import type { AdminAlbum, AdminAlbumPage } from '$lib/api/client';
import AlbumAdminList from './AlbumAdminList.svelte';

vi.mock('$lib/api/http', () => ({ requestJson: vi.fn(), requestEmpty: vi.fn() }));

const albumOf = (index: number): AdminAlbum => ({
  albumId: `album-${String(index)}`,
  title: `作品 ${String(index)}`,
  releaseDate: '2026-09-01',
  artistDisplayName: 'テスト用アーティスト',
  catalogNumber: null,
  isdn: null,
  eventName: null,
  eventDate: null,
  eventPlace: null,
  eventSpaceNumber: null,
  eventNote: null,
  publishedAt: null,
  coverImageUrl: null,
});

describe('作品操作の結果不明からの復帰', () => {
  it.each(['timeout', 'network', 'invalid-response'] as const)(
    '%sでは再送せず対象を再照会する',
    async (reason) => {
      await openList(1);
      vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'failed', reason, message: '通信失敗' });
      await userEvent.click(screen.getByRole('button', { name: '公開する' }));
      await screen.findByText('「作品 1」の公開結果を確認できませんでした。');
      expect(screen.queryByRole('button', { name: '公開する' })).toBeNull();
      expect(screen.queryByRole('button', { name: '再試行' })).toBeNull();
      vi.mocked(requestJson).mockResolvedValueOnce({
        kind: 'ok',
        value: { ...albumOf(1), publishedAt: '2026-09-01' },
      });
      await userEvent.click(screen.getByRole('button', { name: '現在の状態を確認' }));
      await screen.findByText('現在の状態：公開。');
      expect(requestJson).toHaveBeenLastCalledWith(
        expect.stringContaining('/api/v1/admin/albums/album-1'),
        expect.objectContaining({ method: 'GET' }),
      );
      expect(
        vi
          .mocked(requestJson)
          .mock.calls.filter(([url, init]) => init.method === 'POST' && url.endsWith('/publish')),
      ).toHaveLength(1);
    },
  );

  it.each(['delete', 'unpublish'] as const)(
    '%sのサーバー障害で確定ボタンへ戻さず、再照会失敗も変更を再送しない',
    async (operation) => {
      const label = operation === 'delete' ? '削除する' : '非公開にする';
      const published = { ...pageOf(1), items: [{ ...albumOf(1), publishedAt: '2026-09-01' }] };
      await openList(1, published);
      vi.mocked(requestJson).mockResolvedValueOnce({
        kind: 'ok',
        value: {
          deletion: { affectedArticles: [] },
          unpublication: { articlesBecomingUnpublished: [] },
        },
      });
      await userEvent.click(screen.getByRole('button', { name: label }));
      await screen.findByText('影響を受けるものはありません。');
      vi.mocked(requestJson).mockResolvedValueOnce({
        kind: 'failed',
        reason: 'http',
        status: 503,
        message: '障害',
      });
      await userEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: label }),
      );
      await screen.findByRole('region', { name: '操作結果の確認' });
      await waitFor(() => {
        expect(document.body.style.pointerEvents).not.toBe('none');
      });
      const before = vi.mocked(requestJson).mock.calls.length;
      vi.mocked(requestJson).mockResolvedValueOnce({
        kind: 'failed',
        reason: 'timeout',
        message: '照会の期限',
      });
      await userEvent.click(screen.getByRole('button', { name: '現在の状態を確認' }));
      await screen.findByText('照会の期限');
      expect(screen.queryByRole('button', { name: '一覧を読み直す' })).toBeNull();
      vi.mocked(requestJson).mockResolvedValueOnce(
        operation === 'delete'
          ? { kind: 'failed', reason: 'http', status: 404, message: 'ない' }
          : { kind: 'ok', value: albumOf(1) },
      );
      await userEvent.click(screen.getByRole('button', { name: '現在の状態を確認' }));
      await screen.findByText(
        operation === 'delete' ? '現在、この作品は見つかりません。' : '現在の状態：下書き。',
      );
      expect(
        vi
          .mocked(requestJson)
          .mock.calls.slice(before)
          .every(([, init]) => init.method === 'GET'),
      ).toBe(true);
    },
  );

  it('結果不明の再照会で認証が切れても、再認証は照会に戻る', async () => {
    await openList(1);
    vi.mocked(requestJson).mockResolvedValueOnce({
      kind: 'failed',
      reason: 'network',
      message: '通信失敗',
    });
    await userEvent.click(screen.getByRole('button', { name: '公開する' }));
    vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'unauthorized', status: 401 });
    await userEvent.click(await screen.findByRole('button', { name: '現在の状態を確認' }));
    await screen.findByLabelText('管理APIの鍵');
    vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: sessionValue() });
    vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: albumOf(1) });
    await userEvent.type(screen.getByLabelText('管理APIの鍵'), 'test-key');
    await userEvent.click(screen.getByRole('button', { name: '開く' }));
    await screen.findByText('現在の状態：下書き。');
    expect(requestJson).toHaveBeenLastCalledWith(
      expect.stringContaining('/api/v1/admin/albums/album-1'),
      expect.objectContaining({ method: 'GET' }),
    );
  });
});

const pageOf = (total: number, page = 0): AdminAlbumPage => ({
  items: Array.from({ length: Math.max(Math.min(total - page * 50, 50), 0) }, (_, index) =>
    albumOf(page * 50 + index + 1),
  ),
  page,
  size: 50,
  totalElements: total,
  totalPages: Math.ceil(total / 50),
});

const respondWith = (page: AdminAlbumPage): void => {
  vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: page });
};

const sessionValue = () => ({
  token: 'abs_session_pagination',
  expiresAt: new Date(Date.now() + 1_800_000).toISOString(),
});

const openList = async (total: number, initial = pageOf(total)): Promise<void> => {
  vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: sessionValue() });
  await authenticate('test-key');
  respondWith(initial);
  render(AlbumAdminList);
  await screen.findByText(`${String(total)} 件`);
};

const nextPage = () => screen.getByRole('button', { name: '次のページ' });
const previousPage = () => screen.getByRole('button', { name: '前のページ' });

beforeEach(async () => {
  vi.resetAllMocks();
  vi.mocked(requestEmpty).mockResolvedValue({ kind: 'ok', value: undefined });
  await logout().completion;
  sessionStorage.clear();
  vi.clearAllMocks();
});

describe('作品一覧のページ送り', () => {
  it('0件では空の案内を出し、ページを送らない', async () => {
    await openList(0);
    expect(screen.getByText('登録された作品はありません。')).toBeDefined();
    expect(screen.queryByRole('navigation', { name: '作品一覧のページ送り' })).toBeNull();
  });

  it.each([1, 50])('%i件は先頭ページに収まり、両端へ送れない', async (total) => {
    await openList(total);
    expect(screen.getAllByRole('row')).toHaveLength(total + 1);
    expect(screen.getByText(`1–${String(total)} 件目`)).toBeDefined();
    expect(previousPage().hasAttribute('disabled')).toBe(true);
    expect(nextPage().hasAttribute('disabled')).toBe(true);
  });

  it('51件では総件数を保持し、2ページ目から先頭へ戻れる', async () => {
    await openList(51);
    expect(screen.getAllByRole('row')).toHaveLength(51);
    expect(screen.getByText('1–50 件目')).toBeDefined();
    expect(nextPage().hasAttribute('disabled')).toBe(false);
    respondWith(pageOf(51, 1));
    await userEvent.click(nextPage());
    await screen.findByText('51–51 件目');
    expect(screen.getByText('51 件')).toBeDefined();
    expect(screen.getByText('作品 51')).toBeDefined();
    expect(nextPage().hasAttribute('disabled')).toBe(true);
    expect(previousPage().hasAttribute('disabled')).toBe(false);
    expect(requestJson).toHaveBeenLastCalledWith(
      expect.stringContaining('/api/v1/admin/albums?page=1&size=50'),
      expect.anything(),
    );
    respondWith(pageOf(51));
    await userEvent.click(previousPage());
    await screen.findByText('1–50 件目');
  });

  it('移動先が削除でなくなった場合は、存在する最終ページへ戻る', async () => {
    await openList(51);
    respondWith(pageOf(50, 1));
    respondWith(pageOf(50));
    await userEvent.click(nextPage());
    await screen.findByText('50 件');
    expect(screen.getByText('1–50 件目')).toBeDefined();
    expect(nextPage().hasAttribute('disabled')).toBe(true);
  });

  it('移動先の取得失敗は再試行でも同じページを読む', async () => {
    await openList(51);
    vi.mocked(requestJson).mockResolvedValueOnce({
      kind: 'failed',
      reason: 'network',
      message: '通信失敗',
    });
    await userEvent.click(nextPage());
    await screen.findByText('通信失敗');
    respondWith(pageOf(51, 1));
    await userEvent.click(screen.getByRole('button', { name: '再試行' }));
    await screen.findByText('51–51 件目');
    expect(requestJson).toHaveBeenLastCalledWith(
      expect.stringContaining('page=1&size=50'),
      expect.anything(),
    );
  });

  it('移動先で401になっても、再認証後は同じページを開く', async () => {
    await openList(51);
    vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'unauthorized', status: 401 });
    await userEvent.click(nextPage());
    await screen.findByLabelText('管理APIの鍵');
    vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: sessionValue() });
    respondWith(pageOf(51, 1));
    await userEvent.type(screen.getByLabelText('管理APIの鍵'), 'test-key');
    await userEvent.click(screen.getByRole('button', { name: '開く' }));
    await screen.findByText('51–51 件目');
  });

  it('ログアウト後の遅い範囲外応答は、前ページの再照会も起こさない', async () => {
    await openList(51);
    const pending = Promise.withResolvers<ApiResult<AdminAlbumPage>>();
    vi.mocked(requestJson).mockReturnValueOnce(pending.promise);
    await userEvent.click(nextPage());
    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
    const callsBefore = vi.mocked(requestJson).mock.calls.length;
    pending.resolve({ kind: 'ok', value: pageOf(50, 1) });
    await waitFor(() => {
      expect(screen.getByLabelText('管理APIの鍵')).toBeDefined();
    });
    expect(requestJson).toHaveBeenCalledTimes(callsBefore);
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('後ろのページで公開しても、完了後は同じページを読み直す', async () => {
    await openList(51);
    respondWith(pageOf(51, 1));
    await userEvent.click(nextPage());
    await screen.findByText('51–51 件目');
    const pending = Promise.withResolvers<ApiResult<unknown>>();
    vi.mocked(requestJson).mockReturnValueOnce(pending.promise);
    await userEvent.click(screen.getByRole('button', { name: '公開する' }));
    expect(screen.getByText('公開しています。')).toBeDefined();
    expect(screen.queryByRole('button', { name: '前のページ' })).toBeNull();
    respondWith(pageOf(51, 1));
    pending.resolve({ kind: 'ok', value: {} });
    await screen.findByText('51–51 件目');
    expect(requestJson).toHaveBeenLastCalledWith(
      expect.stringContaining('page=1&size=50'),
      expect.anything(),
    );
  });
});
