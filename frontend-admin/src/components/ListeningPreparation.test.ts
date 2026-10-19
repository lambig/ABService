import { render, screen, waitFor } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  listeningEntry: vi.fn(),
  listAlbums: vi.fn(),
  getAlbum: vi.fn(),
  crossfade: vi.fn(),
  registration: vi.fn(),
  listeningPreparation: vi.fn(),
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
const preparation = () => ({
  items: [
    {
      albumId: 'album-fixture',
      title: '未公開の試験作品',
      publishedAt: null,
      audioConfirmed: false,
    },
  ],
  focused: null,
  page: 0,
  totalPages: 1,
  devices: [],
});
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
  api.listeningPreparation.mockResolvedValue(ok(preparation()));
  api.resolveAuthorization.mockResolvedValue(ok(pending));
  api.decideAuthorization.mockResolvedValue(ok({ ...pending, state: 'APPROVED' }));
});
describe('PCから別の試聴機を準備する', () => {
  it('50作品の準備状態を一要求で取得し、順序と配布判定を保つ', async () => {
    const items = Array.from({ length: 50 }, (_, index) => ({
      albumId: `album-${String(index)}`,
      title: `試験作品 ${String(index)}`,
      publishedAt: '2026-01-01T00:00:00Z',
      audioConfirmed: true,
    }));
    api.listeningPreparation.mockResolvedValue(ok({ ...preparation(), items }));
    render(ListeningPreparation);
    await screen.findByText('試験作品 49');
    expect(api.listeningPreparation).toHaveBeenCalledExactlyOnceWith(expect.anything(), 0, null);
    expect(screen.getAllByText(/^試験作品 /).map((row) => row.textContent)).toEqual(
      items.map((album) => album.title),
    );
    expect(screen.getAllByText(/配布対象：確定済みFLACが関連付けられています。/)).toHaveLength(50);
    expect(api.listAlbums).not.toHaveBeenCalled();
    expect(api.getAlbum).not.toHaveBeenCalled();
    expect(api.crossfade).not.toHaveBeenCalled();
    expect(api.registration).not.toHaveBeenCalled();
  });
  it('受付超過は失敗として表示し、一括要求を再操作できる', async () => {
    api.listeningPreparation.mockResolvedValueOnce({
      kind: 'failed',
      status: 503,
      message: '少し待って一覧を更新してください。',
    });
    render(ListeningPreparation);
    await screen.findByText('少し待って一覧を更新してください。');
    expect(screen.queryByText('未公開の試験作品')).toBeNull();
    expect(screen.getByRole('button', { name: '一覧を更新' })).toHaveProperty('disabled', false);
    expect(api.listeningPreparation).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole('button', { name: '一覧を更新' }));
    await screen.findByText('未公開の試験作品');
    expect(api.listeningPreparation).toHaveBeenCalledTimes(2);
  });
  it('ページ外の指定作品とページ移動を同じ一括APIで取得する', async () => {
    window.history.replaceState({}, '', '/?albumId=selected-album');
    api.listeningPreparation.mockResolvedValue(
      ok({
        ...preparation(),
        totalPages: 2,
        focused: {
          albumId: 'selected-album',
          title: '指定作品',
          publishedAt: null,
          audioConfirmed: true,
        },
      }),
    );
    render(ListeningPreparation);
    await screen.findByText(/登録した作品：指定作品/);
    expect(api.listeningPreparation).toHaveBeenCalledExactlyOnceWith(
      expect.anything(),
      0,
      'selected-album',
    );
    await userEvent.click(screen.getByRole('button', { name: '次のページ' }));
    await waitFor(() => {
      expect(api.listeningPreparation).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        'selected-album',
      );
    });
    expect(api.getAlbum).not.toHaveBeenCalled();
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
    expect(api.listeningPreparation).not.toHaveBeenCalled();
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
    api.listeningPreparation.mockResolvedValue(
      ok({
        ...preparation(),
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
