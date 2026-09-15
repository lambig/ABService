import { render, screen, waitFor } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get } from 'svelte/store';

import {
  applySessionResult,
  authenticate,
  isCurrentSession,
  logout,
  sessionState,
  storedSession,
  SESSION_STORAGE_KEY,
} from '$lib/credentials';
import { requestEmpty, requestJson, type ApiResult } from '$lib/api/http';
import { listAlbums } from '$lib/api/client';
import ApiKeyForm from './ApiKeyForm.svelte';
import SessionControls from './SessionControls.svelte';

vi.mock('$lib/api/http', () => ({ requestJson: vi.fn(), requestEmpty: vi.fn() }));

const sessionValue = (token = 'abs_session_first') => ({
  token,
  expiresAt: new Date(Date.now() + 1_800_000).toISOString(),
});
const signedIn = async (token = 'abs_session_first') => {
  vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: sessionValue(token) });
  const result = await authenticate('original-key');
  expect(result?.kind).toBe('ok');
  return result?.kind === 'ok' ? result.value : Promise.reject(new Error('認証失敗'));
};

beforeEach(async () => {
  vi.mocked(requestEmpty).mockResolvedValue({ kind: 'ok', value: undefined });
  await logout().completion;
  sessionStorage.clear();
  vi.clearAllMocks();
});

describe('管理セッション', () => {
  it('キーを交換にだけ使い、トークンと期限だけを保存して通常APIへ渡す', async () => {
    sessionStorage.setItem('abservice.admin.api-key', 'legacy-key');
    const session = await signedIn();
    expect(sessionStorage.getItem('abservice.admin.api-key')).toBeNull();
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '{}')).toEqual({
      token: session.token,
      expiresAt: session.expiresAt,
    });
    expect(sessionStorage.length).toBe(1);
    expect(storedSession()).toEqual(session);
    vi.mocked(requestJson).mockResolvedValueOnce({ kind: 'ok', value: { items: [] } });
    await listAlbums(session);
    expect(requestJson).toHaveBeenLastCalledWith(
      expect.stringContaining('/api/v1/admin/albums'),
      expect.objectContaining({ headers: { Authorization: `Bearer ${session.token}` } }),
    );
  });

  it.each([
    '{broken',
    '{}',
    JSON.stringify({ token: 'abs_session_old', expiresAt: '2000-01-01T00:00:00Z' }),
  ])('破損または期限切れの保存値を捨てる: %s', (raw) => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, raw);
    sessionStorage.setItem('abservice.admin.api-key', 'legacy-key');
    expect(storedSession()).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('ログアウト時は失効通信完了より前に保存を消し、遅い成功を反映しない', async () => {
    const session = await signedIn();
    const pending = Promise.withResolvers<ApiResult<void>>();
    vi.mocked(requestEmpty).mockReturnValueOnce(pending.promise);
    const { completion } = logout();
    expect(sessionStorage.length).toBe(0);
    expect(get(sessionState).session).toBeNull();
    const apply = vi.fn();
    applySessionResult(session, { kind: 'ok', value: [] }, apply);
    expect(apply).not.toHaveBeenCalled();
    pending.resolve({ kind: 'ok', value: undefined });
    await completion;
  });

  it('再認証後の古い401は新しいセッションを消さず、画面にも届かない', async () => {
    const old = await signedIn();
    await logout().completion;
    const current = await signedIn('abs_session_second');
    const apply = vi.fn();
    applySessionResult(old, { kind: 'unauthorized', status: 401 }, apply);
    expect(apply).not.toHaveBeenCalled();
    expect(isCurrentSession(current)).toBe(true);
    expect(storedSession()).toEqual(current);
  });

  it('現在の401は認証を消して再入力へ進める', async () => {
    const session = await signedIn();
    const apply = vi.fn();
    applySessionResult(session, { kind: 'unauthorized', status: 401 }, apply);
    expect(apply).toHaveBeenCalledOnce();
    expect(get(sessionState).session).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('失効通信失敗ではサーバー失効済みと表示しない', async () => {
    await signedIn();
    vi.mocked(requestEmpty).mockResolvedValueOnce({
      kind: 'failed',
      reason: 'network',
      message: 'offline',
    });
    const onLogout = vi.fn();
    render(SessionControls, { onLogout });
    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
    expect(onLogout).toHaveBeenCalledOnce();
    expect(sessionStorage.length).toBe(0);
    expect((await screen.findByRole('status')).textContent).toContain(
      'サーバーでの失効は確認できませんでした',
    );
  });

  it('取り消した交換の遅い成功を保存せず、払い出されたトークンを失効させる', async () => {
    const pending = Promise.withResolvers<ApiResult<unknown>>();
    vi.mocked(requestJson).mockReturnValueOnce(pending.promise);
    const exchange = authenticate('original-key');
    await logout().completion;
    pending.resolve({ kind: 'ok', value: sessionValue() });
    expect(await exchange).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(requestEmpty).toHaveBeenCalledWith(
      expect.stringContaining('/sessions/current'),
      expect.objectContaining({ headers: { Authorization: 'Bearer abs_session_first' } }),
    );
  });

  it.each(['ok', 'unauthorized', 'failed'] as const)(
    '交換の%sでも入力したAPIキーを即時に消す',
    async (kind) => {
      const pending = Promise.withResolvers<ApiResult<unknown>>();
      vi.mocked(requestJson).mockReturnValueOnce(pending.promise);
      const onSubmit = vi.fn();
      render(ApiKeyForm, { message: null, onSubmit });
      const field = screen.getByLabelText<HTMLInputElement>('管理APIの鍵');
      await userEvent.type(field, 'original-key');
      await userEvent.click(screen.getByRole('button', { name: '開く' }));
      expect(field.value).toBe('');
      pending.resolve(
        kind === 'ok'
          ? { kind, value: sessionValue() }
          : kind === 'unauthorized'
            ? { kind, status: 401 }
            : { kind, message: 'offline' },
      );
      await waitFor(() => {
        expect(get(sessionState).authenticating).toBe(false);
      });
      expect(field.value).toBe('');
      expect(onSubmit).toHaveBeenCalledTimes(kind === 'ok' ? 1 : 0);
    },
  );
});
