import { render, screen, waitFor } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  getAlbum: vi.fn(),
  registration: vi.fn(),
  crossfade: vi.fn(),
  uploadAudio: vi.fn(),
  confirmAudio: vi.fn(),
  fingerprint: vi.fn(),
  connectionReady: vi.fn(),
}));
vi.mock('$lib/api/client', () => ({ getAlbum: api.getAlbum }));
vi.mock('$lib/api/private-audio', async (original) => ({
  ...(await original<typeof import('$lib/api/private-audio')>()),
  ...api,
}));
vi.mock('$lib/api/sessions', async (original) => ({
  ...(await original<typeof import('$lib/api/sessions')>()),
  revokeSession: () => Promise.resolve({ kind: 'ok' }),
}));
const { default: AlbumPrivateAudio } = await import('./AlbumPrivateAudio.svelte');
const ALBUM = '0192f8a0-0000-7000-8000-000000000001';
const AUDIO = '0192f8a0-0000-7000-8000-000000000002';
const HASH = 'a'.repeat(64);
const expected = { audioId: AUDIO, byteLength: 16, sha256: HASH };
const row = (state = 'CONFIRMED', sha256 = HASH) => ({
  audioId: AUDIO,
  state,
  createdAt: '2026-01-01T00:00:00Z',
  expiresAt: '2099-01-01T00:00:00Z',
  metadata: state === 'PENDING' ? null : { byteLength: 16, sha256 },
});
const setting = (audioId: string | null = null, revision = 0) => ({
  albumId: ALBUM,
  kind: 'album-crossfade',
  audioId,
  revision,
});
const ok = <T>(value: T) => ({ kind: 'ok', value });
const open = async (resume = true): Promise<void> => {
  window.history.replaceState({}, '', `?albumId=${ALBUM}`);
  sessionStorage.setItem(
    'abservice.admin.session',
    JSON.stringify({
      token: 'abs_session_audio_fixture',
      expiresAt: new Date(Date.now() + 1800000).toISOString(),
    }),
  );
  (resume
    ? () => {
        sessionStorage.setItem(`abservice.admin.audio.${ALBUM}`, JSON.stringify(expected));
      }
    : () => undefined)();
  render(AlbumPrivateAudio);
  await screen.findByText('試験作品');
  await waitFor(() => {
    expect(screen.queryByText('処理中です。')).toBeNull();
  });
};
beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
  api.getAlbum.mockResolvedValue(ok({ title: '試験作品' }));
  api.crossfade.mockResolvedValue(ok(setting()));
  api.registration.mockResolvedValue(ok(row()));
  api.connectionReady.mockResolvedValue(true);
  api.fingerprint.mockResolvedValue({ byteLength: 16, sha256: HASH });
});
describe('管理画面での非公開FLAC登録', () => {
  it('再読込は照会だけを行い、一致した確定済み原本を明示操作で関連付ける', async () => {
    await open();
    expect(api.uploadAudio).not.toHaveBeenCalled();
    expect(api.confirmAudio).not.toHaveBeenCalled();
    api.crossfade.mockResolvedValue(ok(setting(AUDIO, 1)));
    await userEvent.click(screen.getByRole('button', { name: 'この作品の試聴用FLACに設定' }));
    await screen.findByText('この作品の試聴用FLACを設定し、保存結果を確認しました。');
    expect(api.crossfade).toHaveBeenCalledWith(expect.anything(), ALBUM, {
      audioId: AUDIO,
      expectedRevision: 0,
    });
  });
  it.each(['PENDING', 'EXPIRED'])('%sは再送・関連付け・確定復旧を提示しない', async (state) => {
    api.registration.mockResolvedValue(ok(row(state)));
    await open();
    expect(screen.queryByRole('button', { name: 'この作品の試聴用FLACに設定' })).toBeNull();
    expect(screen.queryByRole('button', { name: '保存済み原本の確定を復旧' })).toBeNull();
    expect(api.uploadAudio).not.toHaveBeenCalled();
  });
  it('確定済みでも原本が不一致なら関連付けない', async () => {
    api.registration.mockResolvedValue(ok(row('CONFIRMED', 'b'.repeat(64))));
    await open();
    expect(screen.queryByRole('button', { name: 'この作品の試聴用FLACに設定' })).toBeNull();
  });
  it('送信応答を失ってもGETで原本を照合し、自動再送・自動関連付けを行わない', async () => {
    await open(false);
    api.registration.mockResolvedValueOnce(ok(row('PENDING'))).mockResolvedValue(ok(row()));
    api.uploadAudio.mockResolvedValue({
      kind: 'failed',
      reason: 'network',
      message: 'fixture loss',
    });
    await userEvent.upload(
      screen.getByLabelText('作成済みFLAC'),
      new File(['fixture'], 'crossfade.flac', { type: 'audio/flac' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'FLACを登録する' }));
    await screen.findByText(/送信の応答を確認できませんでした/);
    expect(api.uploadAudio).toHaveBeenCalledTimes(1);
    expect(JSON.parse(sessionStorage.getItem(`abservice.admin.audio.${ALBUM}`) ?? 'null')).toEqual(
      expected,
    );
    expect(api.crossfade.mock.calls.every((args) => args.length === 2)).toBe(true);
    expect(screen.getByRole('button', { name: 'この作品の試聴用FLACに設定' })).toBeTruthy();
  });
  it('世代競合は読戻して表示し、更新を自動再試行しない', async () => {
    await open();
    api.crossfade
      .mockResolvedValueOnce({ kind: 'failed', status: 409, message: 'conflict' })
      .mockResolvedValue(ok(setting('0192f8a0-0000-7000-8000-000000000003', 2)));
    await userEvent.click(screen.getByRole('button', { name: 'この作品の試聴用FLACに設定' }));
    await screen.findByText(/別の変更が入った場合/);
    expect(api.crossfade.mock.calls.filter((args) => args.length === 3)).toHaveLength(1);
  });
  it('ログアウト後の遅延応答から関連付け操作を復活させない', async () => {
    const response = Promise.withResolvers<ReturnType<typeof ok<ReturnType<typeof row>>>>();
    api.registration.mockReturnValue(response.promise);
    window.history.replaceState({}, '', `?albumId=${ALBUM}`);
    sessionStorage.setItem(
      'abservice.admin.session',
      JSON.stringify({
        token: 'abs_session_audio_fixture',
        expiresAt: new Date(Date.now() + 1800000).toISOString(),
      }),
    );
    sessionStorage.setItem(`abservice.admin.audio.${ALBUM}`, JSON.stringify(expected));
    render(AlbumPrivateAudio);
    await screen.findByText('試験作品');
    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
    response.resolve(ok(row()));
    await screen.findByLabelText('管理APIの鍵');
    expect(screen.queryByRole('button', { name: 'この作品の試聴用FLACに設定' })).toBeNull();
  });
});
