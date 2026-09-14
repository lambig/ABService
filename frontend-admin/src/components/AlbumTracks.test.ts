import { EMPTY_TRACK, type TrackDraft } from '$lib/api/track-form';
import { render, screen, within } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import AlbumTracks from './AlbumTracks.svelte';

const trackOf = (trackId: string | null, title: string, tuneTitles: readonly string[] = []) => ({
  ...EMPTY_TRACK,
  trackId,
  title,
  tunes: tuneTitles.map((tuneTitle) => ({
    tuneTitle,
    composerCreditOverride: '',
    arrangerCreditOverride: '',
    linkUrl: '',
  })),
});

const TRACKS: readonly TrackDraft[] = [
  trackOf('track-1', '1曲目', ['チューンA']),
  trackOf('track-2', '2曲目'),
  trackOf('track-3', '3曲目'),
];

const NO_MESSAGES = (): readonly string[] => [];

const propsOf = (tracks: readonly TrackDraft[]) => ({
  tracks,
  disabled: false,
  messagesOf: NO_MESSAGES,
  onChange: (): void => undefined,
});

/*
 * ROWS-ARE-DIRECT-CHILDREN: 行の中にもチューンの一覧（`<ol>`）がある。役割で引くと入れ子の項目まで
 * 拾うため、曲目の一覧の直下だけを行として数える。
 */
const rows = (): readonly HTMLElement[] =>
  [...(document.querySelector('[data-tracks]')?.children ?? [])] as HTMLElement[];

describe('曲目の一覧', () => {
  it('渡された並びの順に、位置から振った番号と名を並べる', () => {
    render(AlbumTracks, propsOf(TRACKS));

    expect(rows().map((row) => within(row).getByText(/^[0-9]+$/u).textContent)).toEqual([
      '1',
      '2',
      '3',
    ]);
    expect(
      rows().map((row) => row.querySelector('[data-track-title]')?.textContent.trim()),
    ).toEqual(['1曲目', '2曲目', '3曲目']);
  });

  it('畳んだ行にも、そのトラックのチューンが出る', () => {
    render(AlbumTracks, propsOf(TRACKS));

    expect(within(rows()[0] as HTMLElement).getByText('チューンA')).toBeTruthy();
  });

  it('既定はどの行も畳まれている', () => {
    render(AlbumTracks, propsOf(TRACKS));

    expect(screen.queryAllByLabelText(/トラック名/u)).toEqual([]);
  });

  it('開いた行だけが入力に変わる', async () => {
    render(AlbumTracks, propsOf(TRACKS));

    await userEvent.click(screen.getByRole('button', { name: '2曲目を開く' }));

    expect(screen.getAllByLabelText(/トラック名/u)).toHaveLength(1);
    expect(within(rows()[1] as HTMLElement).getByLabelText(/トラック名/u)).toBeTruthy();
  });

  it('別の行を開くと、前の行は畳まれる', async () => {
    render(AlbumTracks, propsOf(TRACKS));

    await userEvent.click(screen.getByRole('button', { name: '1曲目を開く' }));
    await userEvent.click(screen.getByRole('button', { name: '2曲目を開く' }));

    expect(screen.getAllByLabelText(/トラック名/u)).toHaveLength(1);
    expect(within(rows()[1] as HTMLElement).getByLabelText(/トラック名/u)).toBeTruthy();
  });

  it('行を外すと、その行が抜けた並びを返す', async () => {
    const onChange = vi.fn();
    render(AlbumTracks, { ...propsOf(TRACKS), onChange });

    await userEvent.click(within(rows()[1] as HTMLElement).getByRole('button', { name: '外す' }));

    expect(onChange).toHaveBeenCalledWith([TRACKS[0], TRACKS[2]]);
  });

  it('下へ動かすと、その行と次の行を入れ替えた並びを返す', async () => {
    const onChange = vi.fn();
    render(AlbumTracks, { ...propsOf(TRACKS), onChange });

    await userEvent.click(within(rows()[0] as HTMLElement).getByRole('button', { name: '下へ' }));

    expect(onChange).toHaveBeenCalledWith([TRACKS[1], TRACKS[0], TRACKS[2]]);
  });

  it('足した行はIDを持たない（新しいトラックとして送られる）', async () => {
    const onChange = vi.fn();
    render(AlbumTracks, { ...propsOf(TRACKS), onChange });

    await userEvent.click(screen.getByRole('button', { name: 'トラックを追加する' }));

    expect(onChange).toHaveBeenCalledWith([...TRACKS, EMPTY_TRACK]);
  });

  it('1件も持たないことを文言で示す', () => {
    render(AlbumTracks, propsOf([]));

    expect(screen.getByText('曲目はありません。')).toBeTruthy();
  });

  it('断られた行は、畳む操作より優先して開く', () => {
    render(AlbumTracks, {
      ...propsOf(TRACKS),
      messagesOf: (path: string): readonly string[] =>
        path === 'tracks[2].title' ? ['タイトルが長すぎます'] : [],
    });

    /*
     * REJECTED-ROW-OPENS: 畳まれたままでは理由が読めない。人が開いていなくても、誤りのある行が開く。
     */
    expect(within(rows()[2] as HTMLElement).getByRole('alert').textContent).toBe(
      'タイトルが長すぎます',
    );
  });

  it('触らせない間は、どの操作も押せない', () => {
    render(AlbumTracks, { ...propsOf(TRACKS), disabled: true });

    expect(
      rows().every((row) =>
        within(row)
          .getAllByRole('button')
          .every((button) => button.hasAttribute('disabled')),
      ),
    ).toBe(true);
    expect(
      screen.getByRole('button', { name: 'トラックを追加する' }).hasAttribute('disabled'),
    ).toBe(true);
  });
});
