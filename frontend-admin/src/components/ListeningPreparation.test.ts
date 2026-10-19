import { render, screen, waitFor } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  listeningEntry: vi.fn(),
  listAlbums: vi.fn(),
  getAlbum: vi.fn(),
  crossfade: vi.fn(),
  registration: vi.fn(),
  listeningDevices: vi.fn(),
  resolveAuthorization: vi.fn(),
  decideAuthorization: vi.fn(),
  revokeDevice: vi.fn(),
}));
vi.mock('$lib/listening-entry', () => ({ listeningEntry: api.listeningEntry }));
vi.mock('$lib/api/client', () => ({ listAlbums: api.listAlbums, getAlbum: api.getAlbum }));
vi.mock('$lib/api/private-audio', () => ({
  crossfade: api.crossfade,
  registration: api.registration,
}));
vi.mock('$lib/api/listening-devices', () => api);
vi.mock('qrcode', () => ({
  default: { toDataURL: () => Promise.resolve('data:image/png;base64,synthetic') },
}));
const { default: ListeningPreparation } = await import('./ListeningPreparation.svelte');
const ok = <T>(value: T) => ({ kind: 'ok', value });
const pending = {
  state: 'PENDING',
  requestId: '0192f8a0-0000-7000-8000-000000000001',
  label: '試験端末',
  expiresAt: '2099-01-01T00:00:00Z',
  deviceId: null,
  retryAfterSeconds: 5,
  code: null,
  proof: null,
  token: null,
};
beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState({}, '', '/');
  sessionStorage.clear();
  sessionStorage.setItem(
    'abservice.admin.session',
    JSON.stringify({
      token: 'abs_session_pairing_fixture',
      expiresAt: new Date(Date.now() + 1800000).toISOString(),
    }),
  );
  api.listeningEntry.mockResolvedValue({
    kind: 'direct',
    preparationUrl: 'https://example.test/admin/listening/',
    listeningUrl: 'https://example.test/offline-player/',
  });
  api.listAlbums.mockResolvedValue(
    ok({
      items: [{ albumId: 'album-fixture', title: '未公開の試験作品', publishedAt: null }],
      totalPages: 1,
    }),
  );
  api.crossfade.mockResolvedValue(ok({ audioId: null }));
  api.listeningDevices.mockResolvedValue(ok({ devices: [] }));
  api.resolveAuthorization.mockResolvedValue(ok(pending));
  api.decideAuthorization.mockResolvedValue(ok({ ...pending, state: 'APPROVED' }));
});
describe('PCから別の試聴機を準備する', () => {
  it('多数の作品と登録状態を同時要求せず、全作品の順序と配布判定を保つ', async () => {
    const items = Array.from({ length: 50 }, (_, index) => ({
      albumId: `album-${String(index)}`,
      title: `試験作品 ${String(index)}`,
      publishedAt: '2026-01-01T00:00:00Z',
    }));
    api.listAlbums.mockResolvedValue(ok({ items, totalPages: 1 }));
    const started = vi.fn();
    const finished = vi.fn();
    const concurrency = vi.fn();
    const receive = async <T>(value: T) => {
      started();
      const active = started.mock.calls.length - finished.mock.calls.length;
      concurrency(active);
      await new Promise((resolve) => setTimeout(resolve, 0));
      finished();
      return active > 1 ? { kind: 'failed', status: 503, message: '同時要求の上限' } : ok(value);
    };
    api.crossfade.mockImplementation((_, id: string) => receive({ audioId: `audio-${id}` }));
    api.registration.mockImplementation(() => receive({ state: 'CONFIRMED' }));
    render(ListeningPreparation);
    await screen.findByText('試験作品 49');
    expect(concurrency.mock.calls.every(([active]) => active === 1)).toBe(true);
    expect(screen.getAllByText(/^試験作品 /).map((row) => row.textContent)).toEqual(
      items.map((album) => album.title),
    );
    expect(screen.getAllByText(/配布対象：確定済みFLACが関連付けられています。/)).toHaveLength(50);
    expect(api.crossfade).toHaveBeenCalledTimes(50);
    expect(api.registration).toHaveBeenCalledTimes(50);
  });
  it('取得失敗後は残りの作品を要求せず、失敗を表示して再操作を可能にする', async () => {
    api.listAlbums.mockResolvedValue(
      ok({
        items: [0, 1, 2].map((index) => ({
          albumId: `album-${String(index)}`,
          title: `試験作品 ${String(index)}`,
          publishedAt: null,
        })),
        totalPages: 1,
      }),
    );
    api.crossfade
      .mockResolvedValueOnce(ok({ audioId: null }))
      .mockResolvedValueOnce({ kind: 'failed', status: 503, message: '取得の失敗' });
    render(ListeningPreparation);
    await screen.findByText('取得の失敗');
    expect(api.crossfade).toHaveBeenCalledTimes(2);
    expect(api.registration).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '一覧を更新' })).toHaveProperty('disabled', false);
    await userEvent.click(screen.getByRole('button', { name: '一覧を更新' }));
    await screen.findByText('試験作品 2');
    expect(api.crossfade).toHaveBeenCalledTimes(5);
  });
  it('配布対象外の理由を示し、明示照合・許可後も端末保存済みとは表示しない', async () => {
    render(ListeningPreparation);
    await screen.findByText('未公開の試験作品');
    expect(screen.getByText(/下書き・音源未設定：作品を公開し、確定済みFLAC/)).toBeDefined();
    expect(screen.getByRole('link', { name: /この端末で開く/ })).toHaveProperty(
      'href',
      'https://example.test/offline-player/',
    );
    await userEvent.type(screen.getByLabelText('接続コード'), 'ABCD-2345');
    await userEvent.click(screen.getByRole('button', { name: 'コードを照合' }));
    await screen.findByText(/端末名：試験端末/);
    expect(api.decideAuthorization).not.toHaveBeenCalled();
    await userEvent.clear(screen.getByLabelText('音源取得の認証期間（日）'));
    expect(screen.getByRole('button', { name: 'この試聴機を許可' })).toHaveProperty(
      'disabled',
      true,
    );
    await userEvent.type(screen.getByLabelText('音源取得の認証期間（日）'), '30');
    await userEvent.click(screen.getByRole('button', { name: 'この試聴機を許可' }));
    await waitFor(() => {
      expect(api.decideAuthorization).toHaveBeenCalledWith(
        expect.anything(),
        pending.requestId,
        true,
        30,
      );
    });
    await screen.findByText(/PCでは端末への保存完了は確認できません/);
  });
  it('SSM画面は管理APIを呼ばず正式HTTPSの再認証へ案内する', async () => {
    api.listeningEntry.mockResolvedValue({
      kind: 'bridge',
      preparationUrl: 'https://example.test/admin/listening/',
      listeningUrl: 'https://example.test/offline-player/',
    });
    render(ListeningPreparation);
    const link = await screen.findByRole('link', { name: '正式な管理画面で試聴機を準備する' });
    expect(link).toHaveProperty('href', 'https://example.test/admin/listening/');
    expect(api.listeningDevices).not.toHaveBeenCalled();
    expect(screen.queryByLabelText('接続コード')).toBeNull();
  });
  it('照合後にコードを変更すると旧端末を許可できず、新しいコードの再照合が必要になる', async () => {
    render(ListeningPreparation);
    await screen.findByText('未公開の試験作品');
    const input = screen.getByLabelText('接続コード');
    await waitFor(() => {
      expect(input).toHaveProperty('disabled', false);
    });
    await userEvent.type(input, 'ABCD-2345');
    expect(input).toHaveProperty('value', 'ABCD-2345');
    await userEvent.click(screen.getByRole('button', { name: 'コードを照合' }));
    expect((await screen.findByText(/照合済みコード/)).textContent).toContain('ABCD-2345');
    await userEvent.clear(input);
    await userEvent.type(input, 'EFGH-6789');
    expect(screen.queryByRole('button', { name: 'この試聴機を許可' })).toBeNull();
    expect(screen.queryByText(/照合済みコード/)).toBeNull();
    expect(api.decideAuthorization).not.toHaveBeenCalled();
    const nextId = '0192f8a0-0000-7000-8000-000000000002';
    api.resolveAuthorization.mockResolvedValue(ok({ ...pending, requestId: nextId }));
    await userEvent.click(screen.getByRole('button', { name: 'コードを照合' }));
    expect((await screen.findByText(/照合済みコード/)).textContent).toContain('EFGH-6789');
    expect(api.resolveAuthorization).toHaveBeenLastCalledWith(expect.anything(), 'EFGH-6789');
    await userEvent.click(screen.getByRole('button', { name: 'この試聴機を許可' }));
    await waitFor(() => {
      expect(api.decideAuthorization).toHaveBeenCalledExactlyOnceWith(
        expect.anything(),
        nextId,
        true,
        30,
      );
    });
  });
  it('受取応答を失った端末の認証IDを示し、同じIDの失効へ進める', async () => {
    api.resolveAuthorization.mockResolvedValue(
      ok({ ...pending, state: 'CONSUMED', deviceId: pending.requestId }),
    );
    api.listeningDevices.mockResolvedValue(
      ok({
        devices: [
          {
            deviceId: pending.requestId,
            label: '試験端末',
            state: 'ACTIVE',
            expiresAt: pending.expiresAt,
          },
        ],
      }),
    );
    api.revokeDevice.mockResolvedValue(ok(undefined));
    render(ListeningPreparation);
    await screen.findByText('未公開の試験作品');
    await userEvent.type(screen.getByLabelText('接続コード'), 'ABCD-2345');
    await userEvent.click(screen.getByRole('button', { name: 'コードを照合' }));
    expect(await screen.findByText(/認証発行済みです。確認用ID/)).toHaveProperty(
      'textContent',
      expect.stringContaining(pending.requestId),
    );
    await userEvent.click(screen.getByRole('button', { name: 'この認証を失効' }));
    await waitFor(() => {
      expect(api.revokeDevice).toHaveBeenCalledWith(expect.anything(), pending.requestId);
    });
  });
});
