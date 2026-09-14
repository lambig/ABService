import type { AdminExternalAudio } from '$lib/api/client';
import { render, screen, within } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import AlbumExternalAudios from './AlbumExternalAudios.svelte';

const audioOf = (externalAudioId: string, displayOrder: number, url: string): AdminExternalAudio =>
  ({ externalAudioId, displayOrder, url }) satisfies AdminExternalAudio;

/**
 * 表示だけを見るための props。
 *
 * 操作の経路（保存・通知）は呼ばれないことを前提に、何もしない実装を渡す。押した先のふるまいは
 * 実スタックの E2E（`admin-album-external-audios.spec.ts`）が見る。
 */
const propsOf = (audios: readonly AdminExternalAudio[]) => ({
  apiKey: 'test-key',
  albumId: 'album-1',
  audios,
  dirty: false,
  saveFirst: (): Promise<'saved' | 'aborted'> => Promise.resolve('saved'),
  onBusy: (): void => undefined,
  onChanged: (): void => undefined,
  onUnauthorized: (): void => undefined,
});

const AUDIOS = [
  audioOf('audio-1', 1, 'https://example.com/one'),
  audioOf('audio-2', 2, 'https://example.com/two'),
  audioOf('audio-3', 3, 'https://example.com/three'),
];

const rows = (): readonly HTMLElement[] => screen.getAllByRole('listitem');

describe('外部音源の一覧', () => {
  it('渡された順に、表示順の番号とURLを並べる', () => {
    render(AlbumExternalAudios, propsOf(AUDIOS));

    expect(rows().map((row) => row.textContent)).toEqual([
      expect.stringContaining('1'),
      expect.stringContaining('2'),
      expect.stringContaining('3'),
    ]);
    expect(rows().map((row) => within(row).getByText(/^https:/).textContent)).toEqual([
      'https://example.com/one',
      'https://example.com/two',
      'https://example.com/three',
    ]);
  });

  it('先頭の行は上へ動かせず、末尾の行は下へ動かせない', () => {
    render(AlbumExternalAudios, propsOf(AUDIOS));

    const upward = rows().map((row) => within(row).getByRole('button', { name: '上へ' }));
    const downward = rows().map((row) => within(row).getByRole('button', { name: '下へ' }));

    expect(upward.map((button) => button.hasAttribute('disabled'))).toEqual([true, false, false]);
    expect(downward.map((button) => button.hasAttribute('disabled'))).toEqual([false, false, true]);
  });

  it('1件も持たないことを文言で示す', () => {
    render(AlbumExternalAudios, propsOf([]));

    expect(screen.getByText('外部音源はありません。')).toBeTruthy();
    expect(screen.queryAllByRole('listitem')).toEqual([]);
  });

  it('作品が作られていない間は、追加できないことを示す', () => {
    render(AlbumExternalAudios, { ...propsOf(AUDIOS), albumId: null });

    expect(screen.getByText('作品を作成すると、外部音源を追加できます。')).toBeTruthy();
    expect(screen.queryAllByRole('listitem')).toEqual([]);
  });
});
