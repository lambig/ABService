import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AdminSession } from '$lib/credentials';
import { createAlbum, updateAlbum } from './client';

vi.mock('$lib/credentials', () => ({ isCurrentSession: () => true }));
const session: AdminSession = {
  token: 'abs_session_fixture',
  expiresAt: '2099-01-01T00:00:00Z',
  generation: Symbol(),
};
afterEach(() => vi.unstubAllGlobals());
const fixture = (items: unknown[], status = 200) => {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(Response.json({ items }, { status }))
    .mockResolvedValueOnce(Response.json({ albumId: 'fixture' }));
  vi.stubGlobal('fetch', fetcher);
  return fetcher;
};
describe('作品の省略名義', () => {
  it('作成時は共通名義を補完し、曲の省略名義は保持する', async () => {
    const fetcher = fixture([{ key: 'site.artist', content: 'Example Circle' }]);
    expect(
      (await createAlbum(session, { title: 'Album', tracks: [{ title: 'Track' }] })).kind,
    ).toBe('ok');
    expect(JSON.parse(fetcher.mock.calls[1]?.[1]?.body as string)).toMatchObject({
      artistDisplayName: 'Example Circle',
      tracks: [{ title: 'Track' }],
    });
  });
  it('更新で空白に戻すと共通名義を使い、世代とソートキーを保持する', async () => {
    const fetcher = fixture([{ key: 'site.artist', content: 'Example Circle' }]);
    await updateAlbum(session, 'fixture', { artistDisplayName: '  ', artistSortKey: 'example' }, 7);
    expect(JSON.parse(fetcher.mock.calls[1]?.[1]?.body as string)).toMatchObject({
      artistDisplayName: 'Example Circle',
      artistSortKey: 'example',
      expectedRevision: 7,
    });
  });
  it('明示名義はサイト設定を読まずそのまま送る', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json({ albumId: 'fixture' }));
    vi.stubGlobal('fetch', fetcher);
    await createAlbum(session, { artistDisplayName: 'Guest Artist' });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(JSON.parse(fetcher.mock.calls[0]?.[1]?.body as string)).toMatchObject({
      artistDisplayName: 'Guest Artist',
    });
  });
  it.each([
    { items: [] },
    { items: [{ key: 'site.artist', content: '   ' }] },
    { items: [{ key: 'site.name', content: 'Site' }] },
  ])('共通名義が使えない場合は案内を出し、作品を保存しない: %j', async ({ items }) => {
    const fetcher = fixture(items);
    const result = await createAlbum(session, {});
    expect(result).toMatchObject({
      kind: 'failed',
    });
    expect(result.kind === 'failed' ? result.message : '').toContain('site.artist');
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('設定取得に失敗したら作品を保存しない', async () => {
    const fetcher = fixture([], 503);
    expect(await createAlbum(session, {})).toMatchObject({ kind: 'failed', status: 503 });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});
