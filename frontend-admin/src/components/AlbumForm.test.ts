import type { AdminAlbumDetail, ApiResult } from '$lib/api/client';
import { render, screen, within } from '@testing-library/svelte';
import { userEvent } from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/*
 * MOCK-THE-SEAM: 管理APIとの継ぎ目だけを差し替える。この画面が持つのは入力と保存の状態遷移で、
 * 通信そのものは `http.ts` 側の検査が見ている。
 */
const { getAlbum, updateAlbum, createAlbum } = vi.hoisted(() => ({
  getAlbum: vi.fn(),
  updateAlbum: vi.fn(),
  createAlbum: vi.fn(),
}));

vi.mock('$lib/api/client', () => ({
  getAlbum,
  updateAlbum,
  createAlbum,
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
  eventCircleName: null,
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

/*
 * SECTIONS-START-COLLAPSED: 区画は既定で畳まれている。読み込めたことは、区画の見出しが出たところで
 * 見る（欄はまだ描かれていない）。
 */
const renderForm = (mode: 'new' | 'edit' = 'edit', search = `?albumId=${ALBUM_ID}`): void => {
  window.history.replaceState({}, '', `/${search}`);
  sessionStorage.setItem(
    'abservice.admin.session',
    JSON.stringify({
      token: 'abs_session_test',
      expiresAt: new Date(Date.now() + 1_800_000).toISOString(),
    }),
  );
  render(AlbumForm, { mode });
};

const openEditor = async (): Promise<void> => {
  renderForm();
  await screen.findByRole('button', { name: '作品を開く' });
};

const openSection = async (heading: string): Promise<void> => {
  await userEvent.click(screen.getByRole('button', { name: `${heading}を開く` }));
};

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  getAlbum.mockResolvedValue({ kind: 'ok', value: detail });
  updateAlbum.mockResolvedValue(rejectedSecondRow);
  createAlbum.mockResolvedValue(rejectedSecondRow);
});

describe('保存と入力エラーの表示', () => {
  it('保存中は変更と二重送信を防ぎ、通信失敗後は入力を保って編集へ戻る', async () => {
    const pending = Promise.withResolvers<ApiResult<unknown>>();
    updateAlbum.mockReturnValueOnce(pending.promise);
    await openEditor();
    await openSection('作品');
    await openSection('初出イベント');
    const title = screen.getByLabelText('タイトル');
    const place = screen.getByLabelText('会場');
    await userEvent.type(title, '改');
    await userEvent.type(place, '入力した会場');
    await userEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(title.matches(':disabled')).toBe(true);
    expect(place.matches(':disabled')).toBe(true);
    const saving = screen.getByRole('button', { name: '保存しています…' });
    expect(saving).toHaveProperty('disabled', true);
    await userEvent.click(saving);
    expect(updateAlbum).toHaveBeenCalledTimes(1);

    pending.resolve({ kind: 'failed', reason: 'network', message: '通信できませんでした。' });
    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      '通信できませんでした。',
    );
    expect(title).toHaveProperty('value', 'アルバム改');
    expect(place).toHaveProperty('value', '入力した会場');
    expect(title.matches(':disabled')).toBe(false);
    expect(place.matches(':disabled')).toBe(false);
    expect(screen.getByRole('button', { name: '保存する' })).toHaveProperty('disabled', false);
    expect(title.getAttribute('aria-invalid')).toBe('false');
    expect(updateAlbum).toHaveBeenCalledTimes(1);
  });

  it('複数の入力エラーを応答の位置へ表示し、関係のない欄へ付けない', async () => {
    const errors = [
      { field: 'title', label: 'タイトル', message: 'タイトルは必須です' },
      { field: 'releaseDate', label: 'リリース日', message: '日付は必須です' },
      { field: 'isdn', label: 'ISDN', message: 'ISDNの形式が違います' },
      { field: 'event.name', label: 'イベント名', message: 'イベント名は必須です' },
    ];
    updateAlbum.mockResolvedValue({
      ...rejectedSecondRow,
      problem: { ...rejectedSecondRow.problem, errors },
    });
    await openEditor();
    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    await screen.findByText('タイトルは必須です');

    errors.forEach(({ field, label, message }) => {
      const container = document.querySelector(`[data-field="${field}"]`) as HTMLElement;
      expect(within(container).getByRole('alert').textContent).toBe(message);
      expect(screen.getByLabelText(label).getAttribute('aria-invalid')).toBe('true');
    });
    expect(screen.getAllByRole('alert')).toHaveLength(errors.length);
    expect(screen.getByLabelText('アーティスト表示名').getAttribute('aria-invalid')).toBe('false');
    expect(screen.getByLabelText('カタログナンバー').getAttribute('aria-invalid')).toBe('false');
  });

  it.each(['', '-1'])(
    '基準額「%s」のエラーを額だけへ表示し、通貨と入力を保持する',
    async (amount) => {
      getAlbum.mockResolvedValue({
        kind: 'ok',
        value: { ...detail, basePrice: { amount: 1500, currency: 'JPY' } },
      });
      updateAlbum.mockResolvedValue({
        ...rejectedSecondRow,
        problem: {
          ...rejectedSecondRow.problem,
          errors: [{ field: 'basePrice.amount', message: '額を確認してください' }],
        },
      });
      await openEditor();
      await openSection('頒布額');
      const input = screen.getByLabelText('基準額');
      await userEvent.clear(input);
      await userEvent.type(input, amount === '' ? '{Backspace}' : amount);
      await userEvent.click(screen.getByRole('button', { name: '保存する' }));

      const field = document.querySelector('[data-field="basePrice.amount"]') as HTMLElement;
      expect(await within(field).findByRole('alert')).toHaveProperty(
        'textContent',
        '額を確認してください',
      );
      expect(screen.getAllByRole('alert')).toHaveLength(1);
      expect(input).toHaveProperty('value', amount);
      expect(screen.getByLabelText('通貨コード（未指定は円）')).toHaveProperty('value', 'JPY');
      expect(updateAlbum).toHaveBeenCalledWith(
        expect.anything(),
        ALBUM_ID,
        expect.objectContaining({
          basePrice: { amount: amount === '' ? undefined : -1, currency: 'JPY' },
        }),
        detail.revision,
      );
    },
  );

  it('新規作成の必須エラーは該当欄だけに出す', async () => {
    createAlbum.mockResolvedValue({
      ...rejectedSecondRow,
      problem: {
        ...rejectedSecondRow.problem,
        errors: [
          { field: 'title', message: 'タイトルは必須です' },
          { field: 'releaseDate', message: '日付は必須です' },
        ],
      },
    });
    renderForm('new', '');
    await userEvent.click(await screen.findByRole('button', { name: '作成する' }));

    expect(await screen.findByText('タイトルは必須です')).toBeTruthy();
    expect(screen.getByText('日付は必須です')).toBeTruthy();
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(screen.getByLabelText('アーティスト表示名').getAttribute('aria-invalid')).toBe('false');
    expect(createAlbum).toHaveBeenCalledTimes(1);
    expect(updateAlbum).not.toHaveBeenCalled();
  });

  it('編集対象が指定されていなければ入力を出さず、APIも呼ばない', async () => {
    renderForm('edit', '');

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      '編集する作品が指定されていません。',
    );
    expect(screen.queryByLabelText('タイトル')).toBeNull();
    expect(getAlbum).not.toHaveBeenCalled();
  });
});

describe('区画の畳み', () => {
  const summaryOf = (heading: string): string | undefined =>
    document
      .querySelector(`[data-section="${heading}"] [data-section-summary]`)
      ?.textContent.trim();

  it('既定はどの区画も畳まれている', async () => {
    await openEditor();

    expect(screen.queryByLabelText('タイトル')).toBeNull();
    expect(screen.queryByLabelText('音源のURL')).toBeNull();
  });

  /*
   * COLLAPSED-READS-LIKE-PUBLIC: 畳んだ区画は公開サイトと同じ読み方の要約で並ぶ。畳み切った画面が
   * その作品の姿になっていないと、開くまで何が入っているのかが分からない。
   */
  it('畳んだ区画は、入っているものを要約で出す', async () => {
    await openEditor();

    expect(summaryOf('作品')).toBe('アルバム / アーティスト / 2026-01-01');
    expect(summaryOf('外部音源')).toBe('3件');
  });

  it('何も入っていない区画は、無いことを示す', async () => {
    await openEditor();

    expect(summaryOf('初出イベント')).toBe('（未入力）');
    expect(summaryOf('曲目')).toBe('（なし）');
  });

  it('開いた区画だけが入力に変わる', async () => {
    await openEditor();

    await openSection('作品');

    expect(screen.getByLabelText('タイトル')).toBeTruthy();
    expect(screen.queryByLabelText('音源のURL')).toBeNull();
  });

  /*
   * REJECTED-SECTION-OPENS: 理由は欄の下に出る。断られた区画が畳まれたままだと、直す先が画面から
   * 消える。
   */
  it('断られた区画は、畳んだままでも開く', async () => {
    await openEditor();

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await screen.findByLabelText('音源のURL')).toBeTruthy();
    expect(screen.queryByLabelText('タイトル')).toBeNull();
  });
});

describe('外部音源の行の誤り', () => {
  it('断られた行の下に理由が出る', async () => {
    await openEditor();
    await openSection('外部音源');

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));

    expect(await within(audioRows()[1] as HTMLElement).findByRole('alert')).toBeTruthy();
    expect(within(audioRows()[0] as HTMLElement).queryByRole('alert')).toBeNull();
  });

  it('並べ替えると、前の誤りは付いて回らない', async () => {
    await openEditor();
    await openSection('外部音源');

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
    await openSection('外部音源');

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
    await openSection('外部音源');

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    await within(audioRows()[1] as HTMLElement).findByRole('alert');

    /* 1行目を外すと、断られた行は位置0へ、無実の3行目が位置1へ繰り上がる */
    await userEvent.click(
      within(audioRows()[0] as HTMLElement).getByRole('button', { name: '外す' }),
    );

    expect(screen.queryAllByRole('alert')).toEqual([]);
  });
});

describe('曲目の行の誤り', () => {
  /** 2つの行に誤りが返る作品。2行目はタイトルを省いている（名はチューン名から決まる） */
  const withTracks = {
    ...detail,
    tracks: [
      {
        trackId: 'track-1',
        trackNo: 1,
        displayTitle: '1曲目',
        title: '1曲目',
        artistDisplayName: null,
        artistSortKey: null,
        tunes: [],
      },
      {
        trackId: 'track-2',
        trackNo: 2,
        displayTitle: 'チューン',
        title: null,
        artistDisplayName: null,
        artistSortKey: null,
        tunes: [
          {
            seq: 1,
            tuneTitle: 'チューン',
            composerCreditOverride: null,
            arrangerCreditOverride: null,
            linkUrl: null,
          },
        ],
      },
    ],
  } satisfies AdminAlbumDetail;

  /* 1回の応答に複数の行の誤りが入る。backend は一覧をまとめて検証して積む */
  const rejectedTwoRows = {
    ...rejectedSecondRow,
    problem: {
      ...rejectedSecondRow.problem,
      errors: [
        { field: 'tracks[0].title', message: 'タイトルが長すぎます' },
        { field: 'tracks[1].tunes[0].linkUrl', message: 'URLとして読めません' },
      ],
    },
  };

  /** 1曲目が2つのチューンを持つ作品。チューンを外したときの位置のずれを見るために使う */
  const withTwoTunes = {
    ...withTracks,
    tracks: [
      {
        trackId: 'track-1',
        trackNo: 1,
        displayTitle: '1曲目',
        title: '1曲目',
        artistDisplayName: null,
        artistSortKey: null,
        tunes: [
          {
            seq: 1,
            tuneTitle: '前半',
            composerCreditOverride: null,
            arrangerCreditOverride: null,
            linkUrl: null,
          },
          {
            seq: 2,
            tuneTitle: '後半',
            composerCreditOverride: null,
            arrangerCreditOverride: null,
            linkUrl: null,
          },
        ],
      },
      {
        trackId: 'track-2',
        trackNo: 2,
        displayTitle: 'チューン',
        title: null,
        artistDisplayName: null,
        artistSortKey: null,
        tunes: [
          {
            seq: 1,
            tuneTitle: 'チューン',
            composerCreditOverride: null,
            arrangerCreditOverride: null,
            linkUrl: null,
          },
        ],
      },
    ],
  } satisfies AdminAlbumDetail;

  /* 1曲目の最初のチューンと、2曲目のチューンに同時に誤りが返る */
  const rejectedFirstTuneAndSecondTrack = {
    ...rejectedSecondRow,
    problem: {
      ...rejectedSecondRow.problem,
      errors: [
        { field: 'tracks[0].tunes[0].linkUrl', message: 'URLとして読めません' },
        { field: 'tracks[1].tunes[0].linkUrl', message: '2曲目のURLが読めません' },
      ],
    },
  };

  const trackRows = (): readonly HTMLElement[] =>
    [...(document.querySelector('[data-tracks]')?.children ?? [])] as HTMLElement[];

  beforeEach(() => {
    getAlbum.mockResolvedValue({ kind: 'ok', value: withTracks });
    updateAlbum.mockResolvedValue(rejectedTwoRows);
  });

  it('読み込んだトラックのタイトルは、省略のまま持つ', async () => {
    await openEditor();
    await openSection('曲目');

    /*
     * RAW-TITLE: 省いたトラックの名はチューン名を繋いだもので、それは出すときの名であって入力では
     * ない。受け取って書き戻すと、省略が明示タイトルへ変わる（#360）。
     */
    expect((trackRows()[1] as HTMLElement).textContent).toContain('（チューン名から組まれます）');
  });

  /*
   * KEEP-OTHER-ROWS: 欄の書き換えでは行の位置が動かないため、位置つきの誤りは同じ行を指したまま。
   * まとめて落とすと、1曲目を1文字直しただけで2曲目の理由まで消え、複数の誤りを1つずつ追いかける
   * ことになる。
   */
  it('欄を1つ書き換えても、同じ応答の誤りは残る', async () => {
    await openEditor();
    await openSection('曲目');

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    expect(await screen.findByText('タイトルが長すぎます')).toBeTruthy();

    await userEvent.type(screen.getByLabelText(/トラック名/u), '改');

    expect(screen.getByText('タイトルが長すぎます')).toBeTruthy();
  });

  /*
   * FIX-WITHOUT-SAVING-IN-BETWEEN: 保存の前に人が1曲目を開いていても、断られた時点で選択は捨てられ、
   * 最初の誤りの行が見える。そこから保存を挟まずに2曲目へ移って直せる——移った後も1曲目には誤りの
   * 印が残る。
   */
  it('複数の誤りを、保存を挟まずに順に直せる', async () => {
    await openEditor();
    await openSection('曲目');

    /* 保存の前から1曲目を開いている（利用者はふつうこの状態で保存する） */
    await userEvent.click(screen.getByRole('button', { name: '1曲目を開く' }));

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    expect(await screen.findByText('タイトルが長すぎます')).toBeTruthy();

    /* 1曲目を直しても、入力欄は消えない（誤りは次の保存まで残るため、開いた行は動かない） */
    await userEvent.type(screen.getByLabelText(/トラック名/u), '改');
    expect(screen.getByLabelText(/トラック名/u)).toHaveProperty('value', '1曲目改');

    /* 保存を挟まずに2曲目を開ける */
    await userEvent.click(screen.getByRole('button', { name: '2曲目を開く' }));

    expect(screen.getByText('URLとして読めません')).toBeTruthy();
    expect(within(trackRows()[0] as HTMLElement).getByText('誤りがあります')).toBeTruthy();
  });

  /*
   * NESTED-POSITIONS-SHIFT: チューンを外すと、そのトラックの中の位置は別の行を指す。残したままだと、
   * 前の行を外したときは繰り上がった行に前の理由が付き、後ろの行を外したときはどの欄にも出ないまま
   * 断られた状態だけが残る。
   */
  it('チューンを外すと、そのトラックの位置の誤りだけを落とす', async () => {
    getAlbum.mockResolvedValue({ kind: 'ok', value: withTwoTunes });
    updateAlbum.mockResolvedValue(rejectedFirstTuneAndSecondTrack);
    await openEditor();
    await openSection('曲目');

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    expect(await screen.findByText('URLとして読めません')).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: '1チューン目を外す' }));

    /* 外したチューンの理由は、繰り上がった行へ付かない */
    expect(screen.queryByText('URLとして読めません')).toBeNull();

    /*
     * 他のトラックの理由は落とさない（位置が動いていない）。1曲目の誤りが消えたので、次の誤りの行が
     * 開いて理由が読める。
     */
    expect(screen.getByText('2曲目のURLが読めません')).toBeTruthy();
  });

  it('行を外すと、位置が別の行を指すため落とす', async () => {
    await openEditor();
    await openSection('曲目');

    await userEvent.click(screen.getByRole('button', { name: '保存する' }));
    await screen.findByText('タイトルが長すぎます');

    await userEvent.click(
      within(trackRows()[0] as HTMLElement).getByRole('button', { name: /^外す$/u }),
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
    await openSection('作品');

    await userEvent.type(screen.getByLabelText('タイトル'), '改');

    expect(screen.getByText(UNSAVED)).toBeTruthy();
  });

  /*
   * COLLAPSED-EDITS-ARE-INVISIBLE: 曲目は畳んだまま足せる。画面の外にある書きかけを知らせるのが、
   * この要素を持つ理由である。
   */
  it('曲目を足しただけでも出る', async () => {
    await openEditor();
    await openSection('曲目');

    await userEvent.click(screen.getByRole('button', { name: 'トラックを追加する' }));

    expect(screen.getByText(UNSAVED)).toBeTruthy();
  });

  /*
   * COMPARE-WHAT-IS-SENT: 触ったかどうかではなく、保存したときに何になるかで比べる。往復して元へ
   * 戻った入力を未保存として数えない。
   */
  it('書き換えを元へ戻すと消える', async () => {
    await openEditor();
    await openSection('作品');

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
