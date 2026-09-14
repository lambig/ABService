import type { ExternalAudioDraft } from '$lib/api/album-form';
import { render, screen, within } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import AlbumExternalAudios from './AlbumExternalAudios.svelte';

const audioOf = (externalAudioId: string | null, url: string): ExternalAudioDraft => ({
  externalAudioId,
  url,
});

const AUDIOS: readonly ExternalAudioDraft[] = [
  audioOf('audio-1', 'https://example.com/one'),
  audioOf('audio-2', 'https://example.com/two'),
  audioOf('audio-3', 'https://example.com/three'),
];

const NO_MESSAGES = (): readonly string[] => [];

const propsOf = (audios: readonly ExternalAudioDraft[]) => ({
  audios,
  disabled: false,
  messagesOf: NO_MESSAGES,
  onEdit: (): void => undefined,
  onReposition: (): void => undefined,
});

const rows = (): readonly HTMLElement[] => screen.getAllByRole('listitem');

const urlsIn = (rowsToRead: readonly HTMLElement[]): readonly (string | null)[] =>
  rowsToRead.map((row) => within(row).getByText(/^https:/).textContent);

describe('外部音源の一覧', () => {
  it('渡された並びの順に、位置から振った番号とURLを並べる', () => {
    render(AlbumExternalAudios, propsOf(AUDIOS));

    expect(rows().map((row) => within(row).getByText(/^[0-9]+$/).textContent)).toEqual([
      '1',
      '2',
      '3',
    ]);
    expect(urlsIn(rows())).toEqual([
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

  /*
   * POSITIONS-SHIFT: 外す・動かすは、以降その位置が別の行を指すようになる操作である。受け取る側が
   * 位置つきの誤りを落とせるよう、追加とは別の口で返す。
   */
  it('行を外すと、その行が抜けた並びを、位置の変わる操作として返す', async () => {
    const onEdit = vi.fn();
    const onReposition = vi.fn();
    render(AlbumExternalAudios, { ...propsOf(AUDIOS), onEdit, onReposition });

    await userEvent.click(within(rows()[1] as HTMLElement).getByRole('button', { name: '外す' }));

    expect(onReposition).toHaveBeenCalledWith([AUDIOS[0], AUDIOS[2]]);
    expect(onEdit).not.toHaveBeenCalled();
  });

  it('下へ動かすと、入れ替えた並びを、位置の変わる操作として返す', async () => {
    const onEdit = vi.fn();
    const onReposition = vi.fn();
    render(AlbumExternalAudios, { ...propsOf(AUDIOS), onEdit, onReposition });

    await userEvent.click(within(rows()[0] as HTMLElement).getByRole('button', { name: '下へ' }));

    expect(onReposition).toHaveBeenCalledWith([AUDIOS[1], AUDIOS[0], AUDIOS[2]]);
    expect(onEdit).not.toHaveBeenCalled();
  });

  /* 末尾への追加では既存の行の位置が変わらない */
  it('足した行はIDを持たず、位置の変わらない操作として返る', async () => {
    const onEdit = vi.fn();
    const onReposition = vi.fn();
    render(AlbumExternalAudios, { ...propsOf(AUDIOS), onEdit, onReposition });

    await userEvent.type(screen.getByLabelText('音源のURL'), 'https://example.com/four');
    await userEvent.click(screen.getByRole('button', { name: '音源を追加する' }));

    expect(onEdit).toHaveBeenCalledWith([...AUDIOS, audioOf(null, 'https://example.com/four')]);
    expect(onReposition).not.toHaveBeenCalled();
  });

  it('行の誤りは、その行の下に出る', () => {
    render(AlbumExternalAudios, {
      ...propsOf(AUDIOS),
      messagesOf: (index: number): readonly string[] =>
        index === 1 ? ['埋め込めるホストではありません'] : [],
    });

    expect(within(rows()[1] as HTMLElement).getByRole('alert').textContent).toBe(
      '埋め込めるホストではありません',
    );
    expect(within(rows()[0] as HTMLElement).queryByRole('alert')).toBeNull();
  });

  it('触らせない間は、どの操作も押せない', () => {
    render(AlbumExternalAudios, { ...propsOf(AUDIOS), disabled: true });

    expect(
      rows().every((row) =>
        within(row)
          .getAllByRole('button')
          .every((button) => button.hasAttribute('disabled')),
      ),
    ).toBe(true);
    expect(screen.getByRole('button', { name: '音源を追加する' }).hasAttribute('disabled')).toBe(
      true,
    );
  });
});
