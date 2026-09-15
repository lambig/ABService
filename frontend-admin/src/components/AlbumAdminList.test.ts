import { render, screen, waitFor } from '@testing-library/svelte';
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

const openList = async (total: number): Promise<void> => {
  vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: sessionValue() });
  await authenticate('test-key');
  respondWith(pageOf(total));
  render(AlbumAdminList);
  await screen.findByText(`${String(total)} 件`);
};

const nextPage = () => screen.getByRole('button', { name: '次のページ' });
const previousPage = () => screen.getByRole('button', { name: '前のページ' });

beforeEach(async () => {
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
