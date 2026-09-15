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
  it('明示操作で入力中のURLだけを試聴し、URL編集・閉じる・追加で終了する', async () => {
    const onEdit = vi.fn();
    const onReposition = vi.fn();
    render(AlbumExternalAudios, { ...propsOf(AUDIOS), onEdit, onReposition });
    const preview = screen.getByRole('button', { name: 'プレビューする' });
    const input = screen.getByLabelText('音源のURL');
    const first = 'https://soundcloud.com/example/one?secret_token=s-test&auto_play=true';
    const frame = (): HTMLElement | null => screen.queryByTitle('追加前の音源の試聴');

    expect(preview.hasAttribute('disabled')).toBe(true);
    await userEvent.type(input, first);
    expect(frame()).toBeNull();
    await userEvent.click(preview);
    const embed = new URL(frame()?.getAttribute('src') ?? '');
    expect(embed.origin).toBe('https://w.soundcloud.com');
    expect(embed.searchParams.get('url')).toBe(first);
    expect(embed.searchParams.get('auto_play')).toBe('false');
    expect(frame()?.getAttribute('referrerpolicy')).toBe('no-referrer');
    expect(onEdit).not.toHaveBeenCalled();
    expect(onReposition).not.toHaveBeenCalled();

    await userEvent.clear(input);
    expect(frame()).toBeNull();
    await userEvent.type(input, 'https://soundcloud.com/example/two');
    expect(frame()).toBeNull();
    await userEvent.click(preview);
    expect(frame()?.getAttribute('src')).toContain(
      encodeURIComponent('https://soundcloud.com/example/two'),
    );
    await userEvent.click(screen.getByRole('button', { name: 'プレビューを閉じる' }));
    expect(frame()).toBeNull();
    await userEvent.click(preview);
    await userEvent.click(screen.getByRole('button', { name: '音源を追加する' }));
    expect(frame()).toBeNull();
    expect(onEdit).toHaveBeenCalledExactlyOnceWith([
      ...AUDIOS,
      audioOf(null, 'https://soundcloud.com/example/two'),
    ]);
  });

  it('未検証のURLは固定プレイヤーの引数に留め、プレビュー失敗を保存判定に使わない', async () => {
    const onEdit = vi.fn();
    render(AlbumExternalAudios, { ...propsOf([]), onEdit });
    const input = 'javascript:alert("example")';
    await userEvent.type(screen.getByLabelText('音源のURL'), input);
    await userEvent.click(screen.getByRole('button', { name: 'プレビューする' }));
    const embed = new URL(screen.getByTitle('追加前の音源の試聴').getAttribute('src') ?? '');
    expect(embed.origin).toBe('https://w.soundcloud.com');
    expect(embed.searchParams.get('url')).toBe(input);
    expect(screen.getByText(/再生できない場合はURLや音源の公開設定/)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: '音源を追加する' }));
    expect(onEdit).toHaveBeenCalledWith([audioOf(null, input)]);
  });

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
    expect(screen.getByRole('button', { name: 'プレビューする' }).hasAttribute('disabled')).toBe(
      true,
    );
  });
});
