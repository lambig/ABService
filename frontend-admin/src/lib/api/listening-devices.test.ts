import { afterEach, describe, expect, it, vi } from 'vitest';
import { listeningPreparation } from './listening-devices';

vi.mock('$lib/credentials', () => ({ isCurrentSession: () => true }));
const session = {
  token: 'synthetic-session',
  expiresAt: '2099-01-01T00:00:00Z',
  generation: Symbol(),
};
afterEach(() => {
  vi.unstubAllGlobals();
});

describe('準備状態の一括API', () => {
  it('ページと指定作品を一つの認証済みGETへ渡す', async () => {
    const value = { items: [], devices: [], page: 2, totalPages: 3, focused: null };
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json(value));
    vi.stubGlobal('fetch', fetcher);
    expect(await listeningPreparation(session, 2, 'synthetic&other=1')).toEqual({
      kind: 'ok',
      value,
    });
    expect(fetcher).toHaveBeenCalledExactlyOnceWith(
      expect.stringMatching(
        /\/api\/v1\/admin\/listening-preparation\?page=2&albumId=synthetic%26other%3D1$/,
      ),
      expect.objectContaining({
        method: 'GET',
        cache: 'no-store',
        redirect: 'error',
      }),
    );
    expect(fetcher.mock.calls[0]?.[1]?.headers).toMatchObject({
      Authorization: 'Bearer synthetic-session',
    });
  });
  it('503の失敗契約を維持し自動で再送しない', async () => {
    const problem = {
      type: 'urn:abservice:error:PREPARATION_BUSY',
      status: 503,
      detail: '少し待って更新してください。',
    };
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(Response.json(problem, { status: 503, headers: { 'Retry-After': '1' } }));
    vi.stubGlobal('fetch', fetcher);
    expect(await listeningPreparation(session, 0, null)).toMatchObject({
      kind: 'failed',
      status: 503,
      problem,
    });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('期限切れの管理セッションでは要求しない', async () => {
    const fetcher = vi.fn<typeof fetch>();
    vi.stubGlobal('fetch', fetcher);
    expect(
      await listeningPreparation({ ...session, expiresAt: '2000-01-01T00:00:00Z' }, 0, null),
    ).toEqual({ kind: 'unauthorized' });
    expect(fetcher).not.toHaveBeenCalled();
  });
});
