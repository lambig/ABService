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

/** 本体の欄と音源の行が同じ応答で断られた場合。1回の 400 に両方が入る */
const rejectedTitleAndSecondRow = {
  ...rejectedSecondRow,
  problem: {
    ...rejectedSecondRow.problem,
    errors: [
      { field: 'title', message: 'タイトルは必須です' },
      { field: 'externalAudios[1].url', message: '埋め込めるホストではありません' },
    ],
  },
};

/** 編集を始めた後に別の保存が入った場合（世代が古い） */
const staleRevision = {
  kind: 'failed' as const,
  reason: 'http' as const,
  status: 409,
  message: '編集を始めた後に更新されています。',
  problem: { type: 'urn:abservice:error:CONFLICTING_UPDATE', status: 409 },
};

/** 集約の不変条件に反する要求（音源URLの重複）。同じ 409 でも直す先は入力にある */
const duplicatedAudioUrl = {
  kind: 'failed' as const,
  reason: 'http' as const,
  status: 409,
  message: '同じURLの音源が2度含まれています。',
  problem: {
    type: 'urn:abservice:error:BUSINESS_RULE_VIOLATION',
    status: 409,
    errors: [
      { field: 'externalAudios', message: 'External audio URL must be unique in this album' },
    ],
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

  it('並べ替えても、本体の欄の誤りは残る', async () => {
    updateAlbum.mockResolvedValue(rejectedTitleAndSecondRow);
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    await within(audioRows()[1] as HTMLElement).findByRole('alert');

    await userEvent.click(
      within(audioRows()[1] as HTMLElement).getByRole('button', { name: '上へ' }),
    );

    /*
     * KEEP-UNTOUCHED-FIELDS: 落とすのは位置が別の行を指すようになった音源の誤りだけ。1回の応答には
     * 本体の欄の誤りも入るため、まとめて捨てると、何も直していない欄から理由が消える。
     */
    expect(screen.getByText('タイトルは必須です')).toBeTruthy();
    expect(screen.queryByText('埋め込めるホストではありません')).toBeNull();
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

describe('未保存の知らせ', () => {
  const UNSAVED = '保存していない変更があります。';

  it('読み込んだ直後は出さない', async () => {
    await openEditor();

    expect(screen.queryByText(UNSAVED)).toBeNull();
  });

  it('本体の欄を書き換えると出る', async () => {
    await openEditor();

    await userEvent.type(screen.getByLabelText('タイトル'), '改');

    expect(screen.getByText(UNSAVED)).toBeTruthy();
  });

  /*
   * COLLAPSED-EDITS-ARE-INVISIBLE: 曲目は畳んだまま足せる。画面の外にある書きかけを知らせるのが、
   * この要素を持つ理由である。
   */
  it('曲目を足しただけでも出る', async () => {
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: 'トラックを追加する' }));

    expect(screen.getByText(UNSAVED)).toBeTruthy();
  });

  /*
   * COMPARE-WHAT-IS-SENT: 触ったかどうかではなく、保存したときに何になるかで比べる。往復して元へ
   * 戻った入力を未保存として数えない。
   */
  it('書き換えを元へ戻すと消える', async () => {
    await openEditor();

    await userEvent.type(screen.getByLabelText('タイトル'), '改');
    await userEvent.type(screen.getByLabelText('タイトル'), '{backspace}');

    expect(screen.queryByText(UNSAVED)).toBeNull();
  });
});

describe('409 の見分け', () => {
  const CONFLICT_HEADING = '編集を始めた後に、別の操作がこの作品を保存しています';

  it('世代が古いときは、読み直しへ導く', async () => {
    updateAlbum.mockResolvedValue(staleRevision);
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByText(CONFLICT_HEADING)).toBeTruthy();
  });

  it('集約の不変条件に反する要求は、理由を出して入力に留める', async () => {
    updateAlbum.mockResolvedValue(duplicatedAudioUrl);
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));

    /*
     * SAME-STATUS-DIFFERENT-CAUSE: 子を集約ルート経由で書くようになり、同じ PUT が世代の競合と業務
     * 違反の両方を 409 で返す（#391）。後者は入力を直せば通るため、読み直しを促す枝へ入れない。
     */
    expect(await screen.findByText(/unique/u)).toBeTruthy();
    expect(screen.queryByText(CONFLICT_HEADING)).toBeNull();
  });
});
