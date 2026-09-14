<script lang="ts">
  import AlbumExternalAudios from '$components/AlbumExternalAudios.svelte';
  import AlbumFormField from '$components/AlbumFormField.svelte';
  import AlbumTracks from '$components/AlbumTracks.svelte';
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import { Button } from '$components/ui/button/index.js';
  import {
    DESCRIPTION_FORMATS,
    EMPTY_DRAFT,
    albumFieldsOf,
    audioDraftsOf,
    draftOf,
    trackDraftsOf,
    withCleared,
    withValue,
    type AlbumDraft,
    type AlbumFieldPath,
    type ExternalAudioDraft,
    type FieldSpec,
  } from '$lib/api/album-form';
  import { TRACK_PATH_PREFIX, trackPathsOf, type TrackDraft } from '$lib/api/track-form';
  import { uploadAsset } from '$lib/api/asset-upload';
  import {
    createAlbum,
    getAlbum,
    updateAlbum,
    type AdminAlbumDetail,
    type AlbumFields,
    type ApiResult,
    type ConfirmedAsset,
  } from '$lib/api/client';
  import { isStaleRevisionConflict } from '$lib/api/http';
  import {
    NO_ERRORS,
    formErrorsOf,
    hasAssignedErrors,
    withoutPathsUnder,
    type FormErrors,
  } from '$lib/api/form-errors';
  import { KEY_STORE, storedApiKey } from '$lib/credentials';
  import { ALBUM_LIST_PATH, albumIdIn } from '$lib/paths';

  /**
   * 作品の新規作成・編集。
   *
   * <p>
   * 検証エラーの位置（`field`）は管理APIが入力パスで返す（#288）。この画面は欄の綴りをその入力パスに
   * 揃えており、code から欄を導く対応表を持たない。位置に対応する欄が無いエラーは捨てず、位置を添えて
   * 全体のエラーとして出す（DECISIONS 29）。
   * </p>
   *
   * <p>
   * 曲目も外部音源も<b>この保存に乗る</b>——作品の子を書く経路は集約ルートに1つしかなく（#391）、送った
   * 並びがそのまま作品の曲目・音源になる。区画が保存のフォームの外に置かれているのは、行を足す入力が
   * Enter で作品の保存を巻き込まないようにするためである。
   * </p>
   */
  type Props = {
    /**
     * 対象の与えられ方。
     *
     * `edit` は問い合わせ文字列から対象を読む（静的な成果物のため、作品ごとの経路を持てない）。
     */
    readonly mode: 'new' | 'edit';
  };

  const { mode }: Props = $props();

  /**
   * 横に並べる欄のまとまり。
   *
   * <p>
   * 対になって読まれるもの（品番とタイトル、名義とそのソートキー、額と通貨）を1行に置く。縦に積むと、
   * どれとどれが組なのかが行の並びからは読めない。
   * </p>
   */
  type FieldRow = readonly FieldSpec[];

  /**
   * 見出しでまとめた入力欄。初出イベントは入れ子の位置（`event.*`）を持つため、まとまりを分ける。
   *
   * <p>
   * `clearing` を持つまとまりは、その欄をまとめて空へ戻す操作を添える。入れ子の項目は1つでも値が
   * 残っていれば送られ、残りの欄が必須として断られるため、外す操作を画面が持たないと利用者が
   * その規則を知っている必要がある。操作は**最後の行の末尾**に置く——外す対象の欄と離すと、何を外す
   * のかが読めない。
   * </p>
   */
  type Section = Readonly<{
    heading: string;
    rows: readonly FieldRow[];
    /** まとまりを外す操作の文言。持たないまとまりは操作を出さない */
    clearing?: string;
  }>;

  const text = (path: AlbumFieldPath, label: string): FieldSpec => ({
    path,
    label,
    kind: 'text',
    choices: [],
  });

  const SECTIONS: readonly Section[] = [
    {
      heading: '作品',
      rows: [
        [text('catalogNumber', 'カタログナンバー'), text('title', 'タイトル')],
        [text('artistDisplayName', 'アーティスト表示名'), text('artistSortKey', 'ソートキー')],
        [
          { path: 'releaseDate', label: 'リリース日', kind: 'date', choices: [] },
          text('isdn', 'ISDN'),
        ],
        [{ path: 'description', label: '概要説明', kind: 'multiline', choices: [] }],
        [
          {
            path: 'descriptionFormat',
            label: '概要説明の形式',
            kind: 'choice',
            choices: DESCRIPTION_FORMATS,
          },
        ],
      ],
    },
    {
      heading: '初出イベント',
      rows: [
        [
          { path: 'event.date', label: '開催日', kind: 'date', choices: [] },
          text('event.name', 'イベント名'),
          text('event.spaceNumber', 'スペース番号'),
        ],
        [text('event.place', '会場'), text('event.note', '補足')],
      ],
    },
    {
      heading: '頒布',
      rows: [
        [
          { path: 'basePrice.amount', label: '基準額', kind: 'number', choices: [] },
          text('basePrice.currency', '通貨コード（未指定は円）'),
        ],
      ],
      clearing: '基準額を解除',
    },
  ];

  /**
   * 原作の出典。
   *
   * <p>
   * まとまりに入れず、曲目の直後へ単独で置く。「「○○」より各曲」のように**曲目そのものを指す一文**で
   * あり、曲目の並びを見た直後に読めるところにある必要がある（#365）。保存は作品の保存に乗るため、
   * フォームの外に出しても `form` 属性でそのフォームへ結び付ける。
   * </p>
   */
  const ORIGINAL_WORK_NOTE: FieldSpec = text('originalWorkNote', '原作の出典（例:「○○」より各曲）');

  /** 作品本体の欄を持つ位置 */
  const FIELD_PATHS: readonly string[] = [
    ...SECTIONS.flatMap((section) => section.rows.flatMap((row) => row.map((field) => field.path))),
    ORIGINAL_WORK_NOTE.path,
  ];

  /**
   * 保存のフォームの名。
   *
   * 曲目の直後に置く欄も、画面の下端に貼り付けた保存の操作も、フォームの囲みの外にある。`form` 属性で
   * このフォームを名指すことで、置き場所に関わらず同じ保存へ乗る。
   */
  const ALBUM_FORM_ID = 'album-form';

  /**
   * 欄を持つ位置。ここに無い位置のエラーは、欄へ割り当てず全体へ出す。
   *
   * <p>
   * 子の位置（`externalAudios[i].url` / `tracks[i].tunes[j].tuneTitle`）は行数で決まるため、いまの入力から
   * 組み立てる。行そのものが無いことを指す位置（`tracks[i]`）は欄に対応しないので、そのまま全体のエラーに
   * なる（DECISIONS 29）。
   * </p>
   */
  const assignablePaths = $derived<readonly string[]>([
    ...FIELD_PATHS,
    ...audios.map((_, index) => audioPathOf(index)),
    ...trackPathsOf(tracks),
  ]);

  /** 外部音源の行を指す位置の接頭辞。行の位置は並びが変われば別の行を指す */
  const AUDIO_PATH_PREFIX = 'externalAudios[';

  /** 外部音源の行の位置。管理APIが検証エラーの `field` として返す綴りと同じ */
  const audioPathOf = (index: number): string => `${AUDIO_PATH_PREFIX}${String(index)}].url`;

  /**
   * 保存の状態。
   *
   * **検証で拒まれた（`invalid`）と、それ以外の理由で保存できなかった（`refused`）を同じ枝にしない。**
   * 前者は欄へ割り当てられる位置つきのエラーを持ち、直す先が入力にある。後者（競合・通信断・応答の
   * 契約違反）は入力を直しても変わらない。畳むと、どちらの理由でも同じ文言を出すことになる。
   */
  type Submission =
    | { readonly kind: 'idle' }
    | { readonly kind: 'saving' }
    | { readonly kind: 'invalid'; readonly errors: FormErrors }
    | { readonly kind: 'conflicted' }
    | { readonly kind: 'refused'; readonly message: string };

  /**
   * カバー画像を差し替えている途中の状態。
   *
   * <p>
   * 保存（{@link Submission}）と分けて持つ。画像は**選んだ時点で保管先へ送られて確定し**、作品への
   * 反映はその後の保存が行う。1つの状態に畳むと、「画像を送っている最中」と「作品を保存している最中」が
   * 区別できなくなる。
   * </p>
   *
   * <p>
   * 断られた理由は文言の列で持つ。位置（`field`）は `file` / `contentType` で返るが、対応する入力欄は
   * 画像を選ぶ操作の1つしかないため、位置ごとに出し分けるものが無い。
   * </p>
   */
  type Upload =
    | { readonly kind: 'idle' }
    | { readonly kind: 'sending' }
    | { readonly kind: 'rejected'; readonly messages: readonly string[] };

  /**
   * 更新する対象。
   *
   * <p>
   * 世代（`revision`）は読み込んだ時点のもので、保存の条件として送り返す（#287）。新規作成では持たない
   * （まだ無いものに世代は無い）。対象と世代を別々の項目にすると、対象があるのに世代が無い状態を作れて
   * しまう。
   * </p>
   */
  type Target = Readonly<{ albumId: string; revision: number }>;

  /**
   * 鍵を入れ直した後に戻る先。
   *
   * <p>
   * 保存が断られた（401/403）ときも入力は失わない。**鍵の正しさは入力の正しさとは別**であり、鍵を
   * 入れ直せば同じ入力から続けられる必要がある。新規作成では鍵の入力時に管理APIを呼ばないため、
   * 誤った鍵でフォームを埋めた場合、これを持たないと最初の保存で全入力が消える。
   * </p>
   */
  type Pending = Readonly<{
    target: Target | null;
    draft: AlbumDraft;
    /** 確定済みのカバー画像の配信先。鍵は `draft` が持ち、こちらは見せる先だけを持つ */
    coverImageUrl: string | null;
    /** いま入力している外部音源。この並びがそのまま保存に乗る */
    audios: readonly ExternalAudioDraft[];
    /** いま入力している曲目。この並びがそのまま保存に乗る */
    tracks: readonly TrackDraft[];
    /** 読み込んだ時点の内容。いまの入力と突き合わせて、未保存かどうかを決める */
    baseline: string;
  }>;

  /**
   * 画面の状態。
   *
   * 鍵待ち・読み込み中・読めなかった・対象が指定されていない・編集中を1つの型で表す。編集中だけが
   * 入力値を持つため、入力値を状態の外に置かない（読み込みに失敗した状態で入力欄が出ることを作らない）。
   */
  type View =
    | {
        readonly kind: 'locked';
        readonly message: string | null;
        /** 鍵を入れ直したら戻る入力。まだ入力が無いときは null */
        readonly pending: Pending | null;
      }
    | { readonly kind: 'loading' }
    | { readonly kind: 'unavailable'; readonly apiKey: string; readonly message: string }
    | { readonly kind: 'unspecified' }
    | {
        readonly kind: 'editing';
        readonly apiKey: string;
        /** 更新する対象と、読み込んだ時点の世代。null は新規作成 */
        readonly target: Target | null;
        readonly draft: AlbumDraft;
        /** 確定済みのカバー画像の配信先。持たないときは null */
        readonly coverImageUrl: string | null;
        /** いま入力している外部音源。この並びがそのまま保存に乗る */
        readonly audios: readonly ExternalAudioDraft[];
        /** いま入力している曲目。この並びがそのまま保存に乗る */
        readonly tracks: readonly TrackDraft[];
        /** 読み込んだ時点の内容。いまの入力と突き合わせて、未保存かどうかを決める */
        readonly baseline: string;
        /**
         * 選ぶ入力を作り直した回数。
         *
         * <p>
         * 同じファイルをもう一度選んでも、入力の値が変わらない限りブラウザは選択を知らせない。**断られた
         * 画像を直してから選び直す**経路と、**外した画像を選び直す**経路は、入力を作り直さないと塞がる。
         * </p>
         *
         * <p>
         * 受け入れられたときは作り直さない。選んだファイルの名が入力に残っているほうが、いま出ている
         * 画像がどれなのかを読める。
         * </p>
         */
        readonly attempts: number;
        readonly submission: Submission;
        readonly upload: Upload;
      };

  let view = $state<View>({ kind: 'loading' });

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  /** 失敗の文言。文言の出所を1つにするため、どの操作の失敗もここを通す */
  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  /**
   * 入力の内容を1つの文字列に畳む。未保存かどうかは、これを読み込んだ時点のものと突き合わせて決める。
   *
   * <p>
   * 畳む前に**送る形**（{@link albumFieldsOf}）へ写す。画面の持ち方ではなく保存したときに何になるかで
   * 比べるため——空白だけの欄を空へ戻すような、送る内容の変わらない書き換えを未保存として数えない。
   * </p>
   */
  const contentOf = (
    draft: AlbumDraft,
    audios: readonly ExternalAudioDraft[],
    tracks: readonly TrackDraft[],
  ): string => JSON.stringify(albumFieldsOf(draft, audios, tracks));

  const editing = (apiKey: string, pending: Pending): View => ({
    kind: 'editing',
    apiKey,
    target: pending.target,
    draft: pending.draft,
    coverImageUrl: pending.coverImageUrl,
    audios: pending.audios,
    tracks: pending.tracks,
    baseline: pending.baseline,
    attempts: 0,
    submission: { kind: 'idle' },
    upload: { kind: 'idle' },
  });

  const lock = (message: string | null, pending: Pending | null): void => {
    view = { kind: 'locked', message, pending };
  };

  /** 読み込んだ作品を、編集の初期値へ写す。突き合わせる基準もここで作る */
  const pendingOfAlbum = (albumId: string, album: AdminAlbumDetail): Pending => {
    const draft = draftOf(album);
    const audios = audioDraftsOf(album);
    const tracks = trackDraftsOf(album);

    return {
      target: { albumId, revision: album.revision },
      draft,
      coverImageUrl: album.coverImageUrl,
      audios,
      tracks,
      baseline: contentOf(draft, audios, tracks),
    };
  };

  const loaded = (apiKey: string, albumId: string, result: ApiResult<AdminAlbumDetail>): View =>
    result.kind === 'ok'
      ? editing(apiKey, pendingOfAlbum(albumId, result.value))
      : result.kind === 'unauthorized'
        ? { kind: 'locked', message: failureTextOf(result), pending: null }
        : { kind: 'unavailable', apiKey, message: failureTextOf(result) };

  const load = async (apiKey: string, albumId: string): Promise<void> => {
    view = { kind: 'loading' };

    const result = await getAlbum(apiKey, albumId);
    KEY_STORE[result.kind](apiKey);
    view = loaded(apiKey, albumId, result);
  };

  /** 新規作成は読み込むものが無い。鍵だけを確かめて入力へ入る */
  const start = (apiKey: string): void => {
    view = editing(apiKey, {
      target: null,
      draft: EMPTY_DRAFT,
      coverImageUrl: null,
      audios: [],
      tracks: [],
      baseline: contentOf(EMPTY_DRAFT, [], []),
    });
  };

  const unspecify = (): void => {
    view = { kind: 'unspecified' };
  };

  /** 状態を差し替えるだけで、待つものが無い経路。読み込みと同じ形（`Promise`）に揃える */
  const settled = (change: () => void): Promise<void> => {
    change();
    return Promise.resolve();
  };

  /**
   * 対象ごとの開き方。
   *
   * 新規作成に対象は無く、編集は問い合わせ文字列から読む。指定が無いまま編集の画面へ来た場合は、
   * 空の新規作成へ落とさない（何も指定していない更新として保存され得る）。
   */
  const openEdit = (apiKey: string, albumId: string | null): Promise<void> =>
    albumId === null ? settled(unspecify) : load(apiKey, albumId);

  const OPEN = {
    new: (apiKey: string): Promise<void> =>
      settled(() => {
        start(apiKey);
      }),
    edit: (apiKey: string): Promise<void> => openEdit(apiKey, albumIdIn(location.search)),
  } satisfies Record<Props['mode'], (apiKey: string) => Promise<void>>;

  const open = (apiKey: string): Promise<void> => OPEN[mode](apiKey);

  /**
   * 鍵を受け取ったときの続け方。
   *
   * 入力を抱えたまま鍵待ちへ戻っていれば、読み直さずその入力へ復帰する。読み直すと、鍵が断られる前に
   * 書いていた内容を捨てることになる。
   */
  const accept = (apiKey: string): Promise<void> => {
    const current = view;
    const pending = current.kind === 'locked' ? current.pending : null;

    return pending === null
      ? open(apiKey)
      : settled(() => {
          view = editing(apiKey, pending);
        });
  };

  /*
   * このコンポーネントは client:only で載るため、ここが動くのはブラウザだけになる（組み立ての時点で
   * sessionStorage と location を触らない）。
   */
  const resume = (): Promise<void> => {
    const apiKey = storedApiKey();
    return apiKey === null
      ? settled(() => {
          lock(null, null);
        })
      : open(apiKey);
  };

  void resume();

  const retry = (): void => {
    const current = view;
    void (current.kind === 'unavailable' ? open(current.apiKey) : Promise.resolve());
  };

  const update = (path: AlbumFieldPath, value: string): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? { ...current, draft: withValue(current.draft, path, value) }
        : current;
  };

  /** まとまりの欄をまとめて空へ戻す。保存すれば、そのまとまりは持たない状態になる */
  const clearSection = (section: Section): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            draft: withCleared(
              current.draft,
              section.rows.flatMap((row) => row.map((field) => field.path)),
            ),
          }
        : current;
  };

  /**
   * カバー画像を差し替える。
   *
   * <p>
   * 選ばれた時点で保管先へ送り、確定できた鍵だけを入力（`coverImageKey`）へ入れる。**送っただけの実体は
   * 配信されない**ため、確定を待たずに鍵を入れると、保存はできるのに画像の出ない作品ができる。
   * </p>
   *
   * <p>
   * 作品への反映はこのあとの保存が行う。ここで作品を保存してしまうと、書きかけの他の欄まで一緒に
   * 保存されることになる（更新は全項目置換のため）。
   * </p>
   */
  const chooseCover = async (apiKey: string, file: File): Promise<void> => {
    withUpload({ kind: 'sending' });
    applyUploadOutcome(apiKey, await uploadAsset(apiKey, file));
  };

  /** 差し替えの状態だけを差し替えた画面 */
  const withUploadOf = (upload: Upload): View => {
    const current = view;
    return current.kind === 'editing' ? { ...current, upload } : current;
  };

  const withUpload = (upload: Upload): void => {
    view = withUploadOf(upload);
  };

  /** 確定できた画像を入力へ入れる。鍵は送る値、URLは画面に出す値で、出所は同じ1つの応答 */
  const withCover = (asset: ConfirmedAsset): View => {
    const current = view;
    return current.kind === 'editing'
      ? {
          ...current,
          draft: withValue(current.draft, 'coverImageKey', asset.assetKey),
          coverImageUrl: asset.url,
          upload: { kind: 'idle' },
        }
      : current;
  };

  /**
   * 断られた理由。
   *
   * 位置つきの検証エラー（形式・サイズ）があればその文言をそのまま出す。無い失敗（通信断・保管先の
   * 拒否）は失敗そのものの文言を出す。どちらも直す先は画像の選び直しにある。
   */
  const uploadMessagesOf = (failure: ApiFailure): readonly string[] => {
    const errors = failure.problem?.errors ?? [];

    return errors.length > 0 ? errors.map((error) => error.message) : [failureTextOf(failure)];
  };

  /** 断られた状態へ移る。選ぶ入力は作り直す（同じ画像を選び直す経路を残すため） */
  const rejectedUpload = (messages: readonly string[]): View => {
    const current = view;
    return current.kind === 'editing'
      ? { ...current, upload: { kind: 'rejected', messages }, attempts: current.attempts + 1 }
      : current;
  };

  const viewAfterUploadFailure = (failure: ApiFailure): View =>
    failure.kind === 'unauthorized'
      ? { kind: 'locked', message: failureTextOf(failure), pending: pendingOf(view) }
      : rejectedUpload(uploadMessagesOf(failure));

  const applyUploadOutcome = (apiKey: string, result: ApiResult<ConfirmedAsset>): void => {
    KEY_STORE[result.kind](apiKey);
    view = result.kind === 'ok' ? withCover(result.value) : viewAfterUploadFailure(result);
  };

  /** 選ばれた画像を受け取る。選ばれていない（取り消された）ときは、いまの画像をそのままにする */
  const pickCover = (files: FileList | null): void => {
    const current = view;
    const file = files?.[0];

    void (current.kind === 'editing' && file !== undefined
      ? chooseCover(current.apiKey, file)
      : Promise.resolve());
  };

  /**
   * カバー画像を外す。
   *
   * 保存すると、作品は画像を持たない状態になる。保管先の実体はそのまま残る——アセットを消す経路を
   * 管理APIが持たないため（#122 で扱う範囲の外）。
   *
   * 選ぶ入力も作り直す。外したのと同じ画像を選び直す経路を残すため。
   */
  const clearCover = (): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            draft: withValue(current.draft, 'coverImageKey', ''),
            coverImageUrl: null,
            upload: { kind: 'idle' },
            attempts: current.attempts + 1,
          }
        : current;
  };

  /** 保存の状態だけを差し替えた画面。入力値は保つ（直す先が入力にあるため、消さない） */
  const withSubmissionOf = (submission: Submission): View => {
    const current = view;
    return current.kind === 'editing' ? { ...current, submission } : current;
  };

  const withSubmission = (submission: Submission): void => {
    view = withSubmissionOf(submission);
  };

  /**
   * 保存できなかった結果を、直す先で分ける。
   *
   * 欄へ割り当てられる位置が1つでもあれば検証エラーとして扱う。400 でも位置が無い（要求全体に関わる
   * もの、欄を持たない位置のもの）場合は、全体のエラーとして出す枝へ渡る。
   */
  const rejection = (failure: ApiFailure, errors: FormErrors): Submission =>
    [hasAssignedErrors(errors), errors.unassigned.length > 0].some(Boolean)
      ? { kind: 'invalid', errors }
      : { kind: 'refused', message: failureTextOf(failure) };

  /**
   * 競合は入力の誤りと分けて扱う。
   *
   * <p>
   * 直す先が入力ではなく「読み直し」にあるため、欄へも全体のエラーへも出さない。古い値を自動で再送も
   * しない（同じ世代で送り直せば再び競合する）。
   * </p>
   *
   * <p>
   * <b>状態コードでは見分けない。</b> 子を集約ルート経由で書くようになり、同じ PUT が集約の不変条件に
   * 反する要求（音源URLの重複・この作品の子でないID）も 409 で返す（#391）。それらは入力を直せば通る
   * ため、読み直しを促す枝へ入れない。
   * </p>
   */
  const rejectionOf = (failure: ApiFailure): Submission =>
    isStaleRevisionConflict(failure)
      ? { kind: 'conflicted' }
      : rejection(failure, formErrorsOf(failure.problem, assignablePaths));

  /**
   * 保存の経路。
   *
   * 更新は編集を始めた時点の世代を条件として送る。新規作成に世代は無い（まだ無いものは誰も更新できない）。
   */
  const save = async (
    apiKey: string,
    target: Target | null,
    fields: AlbumFields,
  ): Promise<ApiResult<unknown>> =>
    target === null
      ? createAlbum(apiKey, fields)
      : updateAlbum(apiKey, target.albumId, fields, target.revision);

  /** いま抱えている入力。編集中でなければ持たない */
  const pendingOf = (current: View): Pending | null =>
    current.kind === 'editing'
      ? {
          target: current.target,
          draft: current.draft,
          coverImageUrl: current.coverImageUrl,
          audios: current.audios,
          tracks: current.tracks,
          baseline: current.baseline,
        }
      : null;

  const viewAfterFailure = (failure: ApiFailure): View =>
    failure.kind === 'unauthorized'
      ? { kind: 'locked', message: failureTextOf(failure), pending: pendingOf(view) }
      : withSubmissionOf(rejectionOf(failure));

  /**
   * 保存できたら一覧へ戻る。
   *
   * 一覧は管理APIから引き直すため、この画面が持つ入力値をそのまま渡さない。離れる経路を枝ごとの表で
   * 持つのは、成功のときだけ画面を捨てる（状態を差し替えない）ためである。
   */
  const LEAVE_AFTER_SAVE = {
    ok: (): void => {
      location.assign(ALBUM_LIST_PATH);
    },
    unauthorized: (): void => undefined,
    failed: (): void => undefined,
  } satisfies Record<ApiResult<unknown>['kind'], () => void>;

  const applySaveOutcome = (apiKey: string, result: ApiResult<unknown>): void => {
    KEY_STORE[result.kind](apiKey);
    view = result.kind === 'ok' ? view : viewAfterFailure(result);
    LEAVE_AFTER_SAVE[result.kind]();
  };

  const submit = (event: SubmitEvent): void => {
    event.preventDefault();

    const current = view;
    void (current.kind === 'editing' &&
    current.submission.kind !== 'saving' &&
    current.upload.kind !== 'sending'
      ? submitWith(
          current.apiKey,
          current.target,
          albumFieldsOf(current.draft, current.audios, current.tracks),
        )
      : Promise.resolve());
  };

  const submitWith = async (
    apiKey: string,
    target: Target | null,
    fields: AlbumFields,
  ): Promise<void> => {
    withSubmission({ kind: 'saving' });
    applySaveOutcome(apiKey, await save(apiKey, target, fields));
  };

  /**
   * 最新を読み込み直す。
   *
   * 競合したときの復帰先。いまの入力は破棄され、保存されている内容に置き換わる（差分の突き合わせは
   * まだ持たない。#287 の受け入れは「古い値を自動で再送しない」ところまで）。
   */
  const reload = (): void => {
    const current = view;
    void (current.kind === 'editing' ? open(current.apiKey) : Promise.resolve());
  };

  /**
   * 外部音源の並びを入力として持ち直す。送るのは保存のときだけ。
   *
   * <p>
   * <b>前回の検証エラーは、ここで落とす。</b> 行の誤りは位置（`externalAudios[i].url`）で返るため、並びが変われば
   * その位置は別の行を指す。残したままにすると、直っていない行からエラーが消え、関係のない行に出る。行は
   * 保存されるまでIDを持たないので、エラーを行へ追従させることもできない。
   * </p>
   */
  const audiosChanged = (audios: readonly ExternalAudioDraft[]): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            audios,
            submission: submissionAfterChildEdit(current.submission, AUDIO_PATH_PREFIX),
          }
        : current;
  };

  /** 曲目の並びを入力として持ち直す。送るのは保存のときだけ（理由は {@link audiosChanged} と同じ） */
  const tracksChanged = (tracks: readonly TrackDraft[]): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            tracks,
            submission: submissionAfterChildEdit(current.submission, TRACK_PATH_PREFIX),
          }
        : current;
  };

  /**
   * 子の並びを変えた後の保存の状態。
   *
   * <p>
   * 落とすのは<b>その子の行に割り当てられたエラーだけ</b>。同じ応答には本体の欄の誤り（`title` など）も、
   * もう一方の子の誤りも一緒に入るため、まとめて捨てると、何も直していない欄のエラーまで消える。
   * </p>
   *
   * <p>
   * 競合や通信断（`conflicted` / `refused`）は入力を変えても消えないため、そのまま残す。
   * </p>
   */
  const submissionAfterChildEdit = (current: Submission, prefix: string): Submission =>
    current.kind === 'invalid' ? invalidOrIdle(withoutPathsUnder(current.errors, prefix)) : current;

  /** 落とした後に残るものが無ければ、拒まれている状態そのものを解く */
  const invalidOrIdle = (errors: FormErrors): Submission =>
    [hasAssignedErrors(errors), errors.unassigned.length > 0].some(Boolean)
      ? { kind: 'invalid', errors }
      : { kind: 'idle' };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。
   */
  const lockMessage = $derived(view.kind === 'locked' ? view.message : null);

  /** 入力を抱えたまま鍵待ちへ戻っていることを伝える文言。抱えていなければ出さない */
  const pendingNotice = $derived(
    view.kind === 'locked' && view.pending !== null
      ? '入力した内容は保持しています。鍵を入れ直すと、続けて保存できます。'
      : null,
  );
  const unavailableMessage = $derived(view.kind === 'unavailable' ? view.message : null);
  const draft = $derived<AlbumDraft>(view.kind === 'editing' ? view.draft : EMPTY_DRAFT);
  const submission = $derived<Submission>(
    view.kind === 'editing' ? view.submission : { kind: 'idle' },
  );
  const errors = $derived<FormErrors>(
    submission.kind === 'invalid' ? submission.errors : NO_ERRORS,
  );
  const refusedMessage = $derived(submission.kind === 'refused' ? submission.message : null);
  const saving = $derived(submission.kind === 'saving');
  const conflicted = $derived(submission.kind === 'conflicted');

  const audios = $derived<readonly ExternalAudioDraft[]>(
    view.kind === 'editing' ? view.audios : [],
  );
  const tracks = $derived<readonly TrackDraft[]>(view.kind === 'editing' ? view.tracks : []);

  /**
   * まだ保存していない書き換えがあるか。
   *
   * <p>
   * 欄ごとに触ったかを覚えず、**いまの入力が保存したときに何になるか**を読み込んだ時点のものと
   * 突き合わせる。曲目の行を足して外すような、往復して元へ戻る操作まで未保存として数えないため。
   * </p>
   */
  const unsaved = $derived(
    view.kind === 'editing' && contentOf(view.draft, view.audios, view.tracks) !== view.baseline,
  );

  /** その行に割り当てられた誤り */
  const audioMessagesOf = (index: number): readonly string[] =>
    errors.byField.get(audioPathOf(index)) ?? [];

  /** 位置に割り当てられた誤り。曲目は入れ子を持つため、位置そのものを受け取る */
  const messagesAt = (path: string): readonly string[] => errors.byField.get(path) ?? [];

  const coverImageUrl = $derived(view.kind === 'editing' ? view.coverImageUrl : null);
  const coverAttempts = $derived(view.kind === 'editing' ? view.attempts : 0);
  const upload = $derived<Upload>(view.kind === 'editing' ? view.upload : { kind: 'idle' });
  const sendingCover = $derived(upload.kind === 'sending');
  const coverMessages = $derived<readonly string[]>(
    upload.kind === 'rejected' ? upload.messages : [],
  );

  /**
   * いま操作を受け付けない状態。
   *
   * 画像を送っている最中の保存も塞ぐ。送り終える前に保存すると、差し替え前の鍵のまま作品が保存され、
   * 画面には新しい画像が出ているのに保存されたのは古い画像、という食い違いが残る。
   */
  const busy = $derived([saving, sendingCover].some(Boolean));

  const messagesOf = (path: AlbumFieldPath): readonly string[] => errors.byField.get(path) ?? [];

  const SAVE_LABELS = { new: '作成する', edit: '保存する' } satisfies Record<Props['mode'], string>;
</script>

{#if view.kind === 'locked'}
  <div class="space-y-4">
    {#if pendingNotice !== null}
      <p class="text-muted-foreground text-sm">{pendingNotice}</p>
    {/if}
    <ApiKeyForm message={lockMessage} onSubmit={(apiKey: string) => void accept(apiKey)} />
  </div>
{:else if view.kind === 'loading'}
  <p class="text-muted-foreground">読み込んでいます。</p>
{:else if view.kind === 'unspecified'}
  <div class="space-y-4">
    <p class="text-destructive" role="alert">編集する作品が指定されていません。</p>
    <a class="text-sm underline underline-offset-4" href={ALBUM_LIST_PATH}>一覧へ戻る</a>
  </div>
{:else if unavailableMessage !== null}
  <div class="max-w-md space-y-4">
    <p class="text-destructive" role="alert">{unavailableMessage}</p>
    <div class="flex items-center gap-4">
      <Button type="button" onclick={retry}>再試行</Button>
      <a class="text-sm underline underline-offset-4" href={ALBUM_LIST_PATH}>一覧へ戻る</a>
    </div>
  </div>
{:else}
  <div class="space-y-8">
    <form id={ALBUM_FORM_ID} class="max-w-2xl space-y-8" onsubmit={submit}>
      <!--
      送ったのはクリックした時点の入力である。保存中も入力を受け付けると、その後の変更は要求に
      入らないまま、成功して一覧へ移ったときに黙って消える。

      画像を送っている最中は塞がない——送り終えても読み直さないので、入力は残る。
    -->
      <fieldset class="space-y-8" disabled={saving}>
        {#each SECTIONS as section (section.heading)}
          <section class="space-y-4">
            <h2 class="text-base font-medium">{section.heading}</h2>

            {#each section.rows as row, index (row[0]?.path)}
              <div class="flex flex-wrap items-end gap-4">
                {#each row as field (field.path)}
                  <div class="min-w-40 flex-1 space-y-1" data-field={field.path}>
                    <AlbumFormField
                      {field}
                      value={draft[field.path]}
                      formId={ALBUM_FORM_ID}
                      messages={messagesOf(field.path)}
                      onValue={(value: string) => {
                        update(field.path, value);
                      }}
                    />
                  </div>
                {/each}

                <!-- 外す操作は、外す対象の欄と同じ行に置く -->
                {#if section.clearing !== undefined && index === section.rows.length - 1}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onclick={() => {
                      clearSection(section);
                    }}
                  >
                    {section.clearing}
                  </Button>
                {/if}
              </div>
            {/each}
          </section>
        {/each}

        <!--
        画像は選んだ時点で送られ、確定できたものだけがここに出る。作品へ反映するのは保存で、
        送るのと反映するのを分けているため、状態の出し方も保存とは別に持つ。
      -->
        <section class="space-y-4" data-field="coverImageKey">
          <div class="flex items-center justify-between gap-4">
            <h2 class="text-base font-medium">カバー画像</h2>
            {#if coverImageUrl !== null}
              <!-- 送っている最中は外せない。外した後に送り終えた画像が入ると、外した操作が黙って覆る -->
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={sendingCover}
                onclick={clearCover}
              >
                カバー画像を外す
              </Button>
            {/if}
          </div>

          {#if coverImageUrl === null}
            <p class="text-muted-foreground text-sm">カバー画像はありません。</p>
          {:else}
            <img
              class="border-input h-40 w-40 rounded-md border object-cover"
              src={coverImageUrl}
              alt="いま選ばれているカバー画像"
              data-cover-image
            />
          {/if}

          <div class="space-y-1">
            <label class="text-sm font-medium" for="album-cover-image">画像を選ぶ</label>
            <!--
            受け入れる形式を並べない。`image/*` はファイルを選ぶ窓の絞り込みで、どの画像形式を
            受け入れるかの判定はバックエンドが持つ。ここへ写すと、増減のたびに2箇所を変えることになる。
          -->
            {#key coverAttempts}
              <input
                id="album-cover-image"
                class="border-input bg-background w-full rounded-md border px-3 py-2"
                type="file"
                accept="image/*"
                disabled={sendingCover}
                onchange={(event) => {
                  pickCover(event.currentTarget.files);
                }}
              />
            {/key}
            <p class="text-muted-foreground text-sm">
              選ぶとすぐに送ります。作品へ反映するには、そのあと保存してください。
            </p>
          </div>

          {#if sendingCover}
            <p class="text-muted-foreground text-sm">画像を送っています…</p>
          {/if}

          {#each coverMessages as message (message)}
            <p class="text-destructive text-sm" role="alert">{message}</p>
          {/each}
        </section>
      </fieldset>
    </form>

    <!--
      OUTSIDE-THE-FORM: 反映は上の保存に乗るが、区画は保存のフォームの外に置く。中に置くと、行を足す
      入力とボタンが作品の保存を巻き込む（Enter も submit になる）。
    -->
    <AlbumTracks {tracks} disabled={busy} messagesOf={messagesAt} onChange={tracksChanged} />

    <!--
      曲目の直後に置くが、保存は作品の保存に乗る。`form` 属性でそのフォームへ結び付けているため、
      フォームの外にあっても一緒に送られる。
    -->
    <fieldset class="max-w-2xl space-y-1" data-field={ORIGINAL_WORK_NOTE.path} disabled={saving}>
      <AlbumFormField
        field={ORIGINAL_WORK_NOTE}
        value={draft[ORIGINAL_WORK_NOTE.path]}
        formId={ALBUM_FORM_ID}
        messages={messagesOf(ORIGINAL_WORK_NOTE.path)}
        onValue={(value: string) => {
          update(ORIGINAL_WORK_NOTE.path, value);
        }}
      />
    </fieldset>

    <AlbumExternalAudios
      {audios}
      disabled={busy}
      messagesOf={audioMessagesOf}
      onChange={audiosChanged}
    />

    <div class="max-w-2xl space-y-8">
      {#if errors.unassigned.length > 0}
        <section class="space-y-1">
          <h2 class="text-base font-medium">どの項目にも紐付かないエラー</h2>
          {#each errors.unassigned as message (message)}
            <p class="text-destructive text-sm" role="alert">{message}</p>
          {/each}
        </section>
      {/if}

      {#if conflicted}
        <section class="space-y-2" role="alert">
          <h2 class="text-destructive text-base font-medium">
            編集を始めた後に、別の操作がこの作品を保存しています
          </h2>
          <p class="text-muted-foreground text-sm">
            いまの入力はそのまま保持しています。このまま保存し直しても、同じ理由で断られます。最新を読み込むと、
            入力は保存されている内容に置き換わります。
          </p>
          <Button type="button" variant="outline" onclick={reload}>最新を読み込む</Button>
        </section>
      {/if}

      {#if refusedMessage !== null}
        <p class="text-destructive text-sm" role="alert">{refusedMessage}</p>
      {/if}
    </div>

    <!--
      STICKY-ACTIONS: 保存の操作を画面の下端に貼り付け、未保存であることをそこに出す。

      曲目も外部音源もこの保存に乗るようになり（#391）、畳んだ行の中にも書きかけが残る。操作が画面の
      上端にしか無いと、**書きかけがあること自体が見えない**まま離脱できてしまう。知らせと操作を同じ
      1箇所に置くのは、知らせを読んだ人がその場で保存できるようにするため。
    -->
    <div
      class="bg-background/95 sticky bottom-0 flex max-w-2xl flex-wrap items-center gap-4 border-t py-3 backdrop-blur"
      data-album-actions
    >
      <Button type="submit" form={ALBUM_FORM_ID} disabled={busy}>
        {saving ? '保存しています…' : SAVE_LABELS[mode]}
      </Button>
      <a class="text-sm underline underline-offset-4" href={ALBUM_LIST_PATH}>やめる</a>

      {#if unsaved}
        <p class="text-muted-foreground text-sm" role="status" data-unsaved>
          保存していない変更があります。
        </p>
      {/if}
    </div>
  </div>
{/if}
