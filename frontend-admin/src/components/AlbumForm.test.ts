import type { AdminAlbumDetail } from '$lib/api/client';
import { render, screen, within } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/*
 * MOCK-THE-SEAM: 管理APIとの継ぎ目だけを差し替える。この画面が持つのは入力と保存の状態遷移で、
 * 通信そのものは `http.ts` 側の検査が見ている。
 */
const { getAlbum, updateAlbum } = vi.hoisted(() => ({
  getAlbum: vi.fn(),
  updateAlbum: vi.fn(),
}));

vi.mock('$lib/api/client', () => ({
  getAlbum,
  updateAlbum,
  createAlbum: vi.fn(),
  issueAssetUploadUrl: vi.fn(),
  confirmAsset: vi.fn(),
}));

const { default: AlbumForm } = await import('./AlbumForm.svelte');

const ALBUM_ID = '01a0233d-d25a-7c3b-924f-236ee154fecc';
const FIRST_URL = 'https://soundcloud.com/example/first';
const SECOND_URL = 'https://example.com/not-embeddable';
const THIRD_URL = 'https://soundcloud.com/example/third';

const detail = {
  albumId: ALBUM_ID,
  revision: 3,
  title: 'アルバム',
  releaseDate: '2026-01-01',
  artistDisplayName: 'アーティスト',
  artistSortKey: null,
  description: null,
  descriptionFormat: 'PLAIN_TEXT',
  catalogNumber: null,
  isdn: null,
  eventName: null,
  eventDate: null,
  eventPlace: null,
  eventSpaceNumber: null,
  eventNote: null,
  publishedAt: null,
  coverImageKey: null,
  coverImageUrl: null,
  basePrice: null,
  originalWorkNote: null,
  externalAudios: [
    { externalAudioId: 'audio-1', displayOrder: 1, url: FIRST_URL },
    { externalAudioId: 'audio-2', displayOrder: 2, url: SECOND_URL },
    { externalAudioId: 'audio-3', displayOrder: 3, url: THIRD_URL },
  ],
  tracks: [],
} satisfies AdminAlbumDetail;

/** 2行目のURLが断られた応答。位置は管理APIが返す綴りのまま */
const rejectedSecondRow = {
  kind: 'failed' as const,
  reason: 'http' as const,
  status: 400,
  message: '入力を受け付けられません。',
  problem: {
    type: 'urn:abservice:error:VALIDATION_ERROR',
    status: 400,
    errors: [{ field: 'externalAudios[1].url', message: '埋め込めるホストではありません' }],
  },
};

const audioRows = (): readonly HTMLElement[] =>
  within(screen.getByRole('list')).getAllByRole('listitem');

const openEditor = async (): Promise<void> => {
  window.history.replaceState({}, '', `?albumId=${ALBUM_ID}`);
  sessionStorage.setItem('abservice.admin.api-key', 'test-key');
  render(AlbumForm, { mode: 'edit' });
  await screen.findByLabelText('タイトル');
};

beforeEach(() => {
  getAlbum.mockResolvedValue({ kind: 'ok', value: detail });
  updateAlbum.mockResolvedValue(rejectedSecondRow);
});

describe('外部音源の行の誤り', () => {
  it('断られた行の下に理由が出る', async () => {
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await within(audioRows()[1] as HTMLElement).findByRole('alert')).toBeTruthy();
    expect(within(audioRows()[0] as HTMLElement).queryByRole('alert')).toBeNull();
  });

  it('並べ替えると、前の誤りは付いて回らない', async () => {
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    await within(audioRows()[1] as HTMLElement).findByRole('alert');

    /*
     * STALE-POSITION: 誤りは位置（externalAudios[i].url）で返る。並びが変われば同じ位置は別の行を
     * 指すため、残したままにすると直っていない行から消え、関係のない行に出る。
     */
    await userEvent.click(
      within(audioRows()[1] as HTMLElement).getByRole('button', { name: '上へ' }),
    );

    expect(screen.queryAllByRole('alert')).toEqual([]);
  });

  it('前の行を外しても、誤りが繰り上がった別の行に出ない', async () => {
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    await within(audioRows()[1] as HTMLElement).findByRole('alert');

    /* 1行目を外すと、断られた行は位置0へ、無実の3行目が位置1へ繰り上がる */
    await userEvent.click(
      within(audioRows()[0] as HTMLElement).getByRole('button', { name: '外す' }),
    );

    expect(screen.queryAllByRole('alert')).toEqual([]);
  });
});
