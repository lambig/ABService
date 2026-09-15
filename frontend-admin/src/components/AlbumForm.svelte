<script lang="ts">
  import AlbumExternalAudios from '$components/AlbumExternalAudios.svelte';
  import AlbumFormField from '$components/AlbumFormField.svelte';
  import AlbumSection from '$components/AlbumSection.svelte';
  import AlbumTracks from '$components/AlbumTracks.svelte';
  import SessionControls from '$components/SessionControls.svelte';
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
  import {
    TRACK_PATH_PREFIX,
    trackPathsOf,
    tunesPathPrefixOf,
    type TrackDraft,
  } from '$lib/api/track-form';
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
  import { applySessionResult, storedSession, type AdminSession } from '$lib/credentials';
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
    /**
     * 畳んだときに出す要約。
     *
     * まとまりごとに書く——公開サイトで何がどう読まれるかはまとまりによって違い、欄を機械的に並べると
     * 「ラベル: 値」の羅列になって、畳んだ意味が無くなる。
     */
    summaryOf: (draft: AlbumDraft) => string;
  }>;

  const text = (path: AlbumFieldPath, label: string): FieldSpec => ({
    path,
    label,
    kind: 'text',
    choices: [],
  });

  /** 要約に出す値。空白だけの欄は入っていないものとして落とす */
  const filled = (parts: readonly string[]): readonly string[] =>
    parts.filter((part) => part.trim() !== '');

  /** 何も入っていない区画は、無いことを示す。空の要約では畳んだ行が読めない */
  const summaryLine = (parts: readonly string[], separator: string): string =>
    filled(parts).length === 0 ? '（未入力）' : filled(parts).join(separator);

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
      /* 公開サイトが作品を名指すときの読み方（品番・タイトル・名義・リリース日） */
      summaryOf: (draft) =>
        summaryLine(
          [draft.catalogNumber, draft.title, draft.artistDisplayName, draft.releaseDate],
          ' / ',
        ),
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
      /* 公開サイトの初出イベントと同じ並び（名・日付・会場・スペース番号） */
      summaryOf: (draft) =>
        summaryLine(
          [
            draft['event.name'],
            draft['event.date'],
            draft['event.place'],
            draft['event.spaceNumber'],
          ],
          ' ',
        ),
    },
    {
      heading: '頒布額',
      rows: [
        [
          { path: 'basePrice.amount', label: '基準額', kind: 'number', choices: [] },
          text('basePrice.currency', '通貨コード（未指定は円）'),
        ],
      ],
      clearing: '基準額を解除',
      /*
       * 入力された値をそのまま並べる。公開サイトの整形（通貨ごとの最小単位）は
       * `frontend-public` の `formatPrice` が持っており、写すと規則が2箇所に散る。
       */
      summaryOf: (draft) =>
        summaryLine([draft['basePrice.amount'], draft['basePrice.currency']], ' '),
    },
  ];

  /**
   * 原作の出典。
   *
   * <p>
   * 欄の持ち主は作品だが、置き場は<b>曲目の区画の中</b>である。「「○○」より各曲」のように<b>曲単位で
   * 特定しないまま曲目全体を指す一文</b>であり、読む場所も直す場所も曲目の並びの直後になる（#365）。
   * 畳んだときの要約にも曲数と並べて出す——出典だけが消えると、入っていることが読めない。
   * </p>
   *
   * <p>
   * 保存は作品の保存に乗る。区画が保存のフォームの外にあるため、`form` 属性でそのフォームへ結び付ける。
   * </p>
   */
  const ORIGINAL_WORK_NOTE: FieldSpec = text('originalWorkNote', '原作の出典（例:「○○」より各曲）');

  /** そのまとまりが持つ欄の位置 */
  const pathsIn = (section: Section): readonly string[] =>
    section.rows.flatMap((row) => row.map((field) => field.path));

  /** 作品本体の欄を持つ位置 */
  const FIELD_PATHS: readonly string[] = [...SECTIONS.flatMap(pathsIn), ORIGINAL_WORK_NOTE.path];

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
    | { readonly kind: 'unavailable'; readonly session: AdminSession; readonly message: string }
    | { readonly kind: 'unspecified' }
    | {
        readonly kind: 'editing';
        readonly session: AdminSession;
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
        /**
         * 検証で断られた回数。
         *
         * <p>
         * <b>新しい検証結果が届いたことを子の区画へ伝えるために数える。</b> 誤りの中身で見分けると、
         * 同じ誤りがもう一度返ったときに別の結果だと分からない。区画はこの数が変わったときだけ、
         * 開く行を最初の誤りの行へ選び直す（それ以外は人が選んだ行を保つ）。
         * </p>
         */
        readonly rejections: number;
        readonly submission: Submission;
        readonly upload: Upload;
      };

  let view = $state<View>({ kind: 'loading' });

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  /** 失敗の文言。文言の出所を1つにするため、どの操作の失敗もここを通す */
  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized'
      ? 'セッションが終了しました。鍵を入力して再認証してください。'
      : failure.message;

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

  const editing = (session: AdminSession, pending: Pending): View => ({
    kind: 'editing',
    session,
    target: pending.target,
    draft: pending.draft,
    coverImageUrl: pending.coverImageUrl,
    audios: pending.audios,
    tracks: pending.tracks,
    baseline: pending.baseline,
    attempts: 0,
    rejections: 0,
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

  const loaded = (
    session: AdminSession,
    albumId: string,
    result: ApiResult<AdminAlbumDetail>,
  ): View =>
    result.kind === 'ok'
      ? editing(session, pendingOfAlbum(albumId, result.value))
      : result.kind === 'unauthorized'
        ? { kind: 'locked', message: failureTextOf(result), pending: null }
        : { kind: 'unavailable', session, message: failureTextOf(result) };

  const load = async (session: AdminSession, albumId: string): Promise<void> => {
    view = { kind: 'loading' };

    const result = await getAlbum(session, albumId);
    return applySessionResult(session, result, () => {
      view = loaded(session, albumId, result);
    });
  };

  /** 新規作成は読み込むものが無い。鍵だけを確かめて入力へ入る */
  const start = (session: AdminSession): void => {
    view = editing(session, {
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
  const openEdit = (session: AdminSession, albumId: string | null): Promise<void> =>
    albumId === null ? settled(unspecify) : load(session, albumId);

  const OPEN = {
    new: (session: AdminSession): Promise<void> =>
      settled(() => {
        start(session);
      }),
    edit: (session: AdminSession): Promise<void> => openEdit(session, albumIdIn(location.search)),
  } satisfies Record<Props['mode'], (session: AdminSession) => Promise<void>>;

  const open = (session: AdminSession): Promise<void> => OPEN[mode](session);

  /**
   * 鍵を受け取ったときの続け方。
   *
   * 入力を抱えたまま鍵待ちへ戻っていれば、読み直さずその入力へ復帰する。読み直すと、鍵が断られる前に
   * 書いていた内容を捨てることになる。
   */
  const accept = (session: AdminSession): Promise<void> => {
    const current = view;
    const pending = current.kind === 'locked' ? current.pending : null;

    return pending === null
      ? open(session)
      : settled(() => {
          view = editing(session, pending);
        });
  };

  /*
   * このコンポーネントは client:only で載るため、ここが動くのはブラウザだけになる（組み立ての時点で
   * sessionStorage と location を触らない）。
   */
  const resume = (): Promise<void> => {
    const session = storedSession();
    return session === null
      ? settled(() => {
          lock(null, null);
        })
      : open(session);
  };

  void resume();

  const retry = (): void => {
    const current = view;
    void (current.kind === 'unavailable' ? open(current.session) : Promise.resolve());
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
  const chooseCover = async (session: AdminSession, file: File): Promise<void> => {
    withUpload({ kind: 'sending' });
    applyUploadOutcome(session, await uploadAsset(session, file));
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

  const applyUploadOutcome = (session: AdminSession, result: ApiResult<ConfirmedAsset>): void => {
    return applySessionResult(session, result, () => {
      view = result.kind === 'ok' ? withCover(result.value) : viewAfterUploadFailure(result);
    });
  };

  /** 選ばれた画像を受け取る。選ばれていない（取り消された）ときは、いまの画像をそのままにする */
  const pickCover = (files: FileList | null): void => {
    const current = view;
    const file = files?.[0];

    void (current.kind === 'editing' && file !== undefined
      ? chooseCover(current.session, file)
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

  /**
   * 検証で断られた結果だけを数える。
   *
   * 同じ誤りがもう一度返っても別の結果として数える——子の区画はこの数の変化で「新しい検証結果が来た」
   * と判じ、開く行を選び直す。
   */
  const rejectionsAfter = (current: number, submission: Submission): number =>
    current + (submission.kind === 'invalid' ? 1 : 0);

  /** 保存の状態だけを差し替えた画面。入力値は保つ（直す先が入力にあるため、消さない） */
  const withSubmissionOf = (submission: Submission): View => {
    const current = view;
    return current.kind === 'editing'
      ? {
          ...current,
          submission,
          rejections: rejectionsAfter(current.rejections, submission),
        }
      : current;
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
    session: AdminSession,
    target: Target | null,
    fields: AlbumFields,
  ): Promise<ApiResult<unknown>> =>
    target === null
      ? createAlbum(session, fields)
      : updateAlbum(session, target.albumId, fields, target.revision);

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

  const applySaveOutcome = (session: AdminSession, result: ApiResult<unknown>): void => {
    return applySessionResult(session, result, () => {
      view = result.kind === 'ok' ? view : viewAfterFailure(result);
      LEAVE_AFTER_SAVE[result.kind]();
    });
  };

  const submit = (event: SubmitEvent): void => {
    event.preventDefault();

    const current = view;
    void (current.kind === 'editing' &&
    current.submission.kind !== 'saving' &&
    current.upload.kind !== 'sending'
      ? submitWith(
          current.session,
          current.target,
          albumFieldsOf(current.draft, current.audios, current.tracks),
        )
      : Promise.resolve());
  };

  const submitWith = async (
    session: AdminSession,
    target: Target | null,
    fields: AlbumFields,
  ): Promise<void> => {
    withSubmission({ kind: 'saving' });
    applySaveOutcome(session, await save(session, target, fields));
  };

  /**
   * 最新を読み込み直す。
   *
   * 競合したときの復帰先。いまの入力は破棄され、保存されている内容に置き換わる（差分の突き合わせは
   * まだ持たない。#287 の受け入れは「古い値を自動で再送しない」ところまで）。
   */
  const reload = (): void => {
    const current = view;
    void (current.kind === 'editing' ? open(current.session) : Promise.resolve());
  };

  /**
   * 外部音源の並びを入力として持ち直す。送るのは保存のときだけ。
   *
   * <p>
   * <b>前回の検証エラーはそのまま残す。</b> 末尾へ足しただけでは既存の行の位置は変わらず、
   * `externalAudios[i].url` は同じ行を指したままである。落とすと、何も直していない行から理由が消える。
   * </p>
   */
  const audiosEdited = (audios: readonly ExternalAudioDraft[]): void => {
    const current = view;
    view = current.kind === 'editing' ? { ...current, audios } : current;
  };

  /**
   * 外部音源の行を外した・動かした後。
   *
   * <p>
   * <b>その子の行に割り当てられたエラーは、ここで落とす。</b> 位置（`externalAudios[i].url`）は並びが
   * 変われば別の行を指す。残したままにすると、直っていない行から消えて関係のない行に出る。行は保存
   * されるまでIDを持たないので、エラーを行へ追従させることもできない。
   * </p>
   */
  const audiosRepositioned = (audios: readonly ExternalAudioDraft[]): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            audios,
            submission: submissionAfterReposition(current.submission, AUDIO_PATH_PREFIX),
          }
        : current;
  };

  /**
   * 曲目の並びを入力として持ち直す。送るのは保存のときだけ。
   *
   * <p>
   * <b>前回の検証エラーはそのまま残す。</b> 欄の書き換えでも末尾への追加でも既存の行の位置は変わらず、
   * `tracks[i].tunes[j].field` は同じ行を指したままである。1回の応答には複数の行の誤りが同時に入り得る
   * ため（`Result.all` が積む）、ここで落とすと<b>1曲目を1文字直しただけで3曲目の理由まで消える</b>。
   * </p>
   */
  const tracksEdited = (tracks: readonly TrackDraft[]): void => {
    const current = view;
    view = current.kind === 'editing' ? { ...current, tracks } : current;
  };

  /**
   * 位置が変わった範囲。
   *
   * 曲目そのものを外した・動かしたときは曲目全体、行の中のチューンを外したときは<b>そのトラックの下
   * だけ</b>。チューンの並びが変わっても、他のトラックの位置は動かない。
   */
  const stalePrefixOf = (within: number | null): string =>
    within === null ? TRACK_PATH_PREFIX : tunesPathPrefixOf(within);

  /** 曲目の行の位置が変わった後（理由は {@link audiosRepositioned} と同じ） */
  const tracksRepositioned = (tracks: readonly TrackDraft[], within: number | null): void => {
    const current = view;
    view =
      current.kind === 'editing'
        ? {
            ...current,
            tracks,
            submission: submissionAfterReposition(current.submission, stalePrefixOf(within)),
          }
        : current;
  };

  /**
   * 子の並びが変わった後の保存の状態。
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
  const submissionAfterReposition = (current: Submission, prefix: string): Submission =>
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

  /** 検証で断られた回数。子の区画はこの変化で、開く行を最初の誤りの行へ選び直す */
  const rejections = $derived(view.kind === 'editing' ? view.rejections : 0);

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

  /**
   * 人が開いた区画。
   *
   * <p>
   * <b>既定はどれも畳んでいる。</b> 作品1件の入力は縦に長く、全部が開いたままでは、いま何を編集して
   * いるのかを見失う。畳み切った画面はその作品の姿（公開サイトと同じ読み方）になり、直したい区画だけを
   * 開いて入力に変える。
   * </p>
   *
   * <p>
   * 行（トラック・チューン）と違い、<b>同時にいくつ開いてもよい</b>。区画はそれぞれ別の事柄で、突き合わせ
   * ながら直すことがある。
   * </p>
   */
  let openSections = $state<ReadonlySet<string>>(new Set());

  const toggleSection = (heading: string): void => {
    openSections = openSections.has(heading)
      ? new Set([...openSections].filter((open) => open !== heading))
      : new Set([...openSections, heading]);
  };

  /** その位置のどれかに誤りが割り当てられているか */
  const rejectedAt = (paths: readonly string[]): boolean =>
    paths.some((path) => messagesAt(path).length > 0);

  /** その接頭辞の下のどこかに誤りが割り当てられているか。子の位置は行数で決まるため綴りで引く */
  const rejectedUnder = (prefix: string): boolean =>
    [...errors.byField.keys()].some((path) => path.startsWith(prefix));

  /**
   * 開いている区画。
   *
   * 断られた区画は畳めない——理由は欄の下に出るため、畳んだままでは直す先が画面から消える。
   */
  const shown = (heading: string, rejected: boolean): boolean =>
    [rejected, openSections.has(heading)].some(Boolean);

  /*
   * 畳んだ区画に出す要約。欄を持たない区画（カバー画像・曲目・外部音源）は入っているものの数で読む
   * ——どれも中身が並びや画像で、1行へ畳むと元の読み方にならない。
   */
  const coverSummary = $derived(coverImageUrl === null ? '（なし）' : '設定済み');
  const audiosSummary = $derived(audios.length === 0 ? '（なし）' : `${String(audios.length)}件`);

  /* 曲目は原作の出典も抱える。畳んだときに出典が消えると、入っていることが読めない */
  const trackParts = $derived(
    filled([tracks.length === 0 ? '' : `${String(tracks.length)}曲`, draft.originalWorkNote]),
  );
  const tracksSummary = $derived(trackParts.length === 0 ? '（なし）' : trackParts.join(' / '));

  /** 曲目の誤り。行の位置つきのものと、原作の出典のどちらも直す先はこの区画にある */
  const tracksRejected = $derived(
    [rejectedUnder(TRACK_PATH_PREFIX), rejectedAt([ORIGINAL_WORK_NOTE.path])].some(Boolean),
  );

  /** カバー画像の誤り。位置を持つ検証エラーと、送るのに失敗した理由のどちらも直す先はこの区画にある */
  const coverRejected = $derived(
    [rejectedAt(['coverImageKey']), coverMessages.length > 0].some(Boolean),
  );

  const SAVE_LABELS = { new: '作成する', edit: '保存する' } satisfies Record<Props['mode'], string>;
</script>

<SessionControls
  onLogout={() => {
    lock(null, null);
  }}
/>

{#if view.kind === 'locked'}
  <div class="space-y-4">
    {#if pendingNotice !== null}
      <p class="text-muted-foreground text-sm">{pendingNotice}</p>
    {/if}
    <ApiKeyForm message={lockMessage} onSubmit={(session: AdminSession) => void accept(session)} />
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
  <!--
    狭い幅では操作が本文の上に重なるため、その分だけ下を空ける。空けないと、いちばん下の入力へ永久に
    届かない。広い幅は右へ逃げるので重ならない。
  -->
  <div class="space-y-8 pb-24 lg:pb-0">
    <form id={ALBUM_FORM_ID} class="max-w-2xl space-y-8" onsubmit={submit}>
      <!--
      送ったのはクリックした時点の入力である。保存中も入力を受け付けると、その後の変更は要求に
      入らないまま、成功して一覧へ移ったときに黙って消える。

      画像を送っている最中は塞がない——送り終えても読み直さないので、入力は残る。
    -->
      <fieldset class="space-y-8" disabled={saving}>
        {#each SECTIONS as section (section.heading)}
          <AlbumSection
            heading={section.heading}
            summary={section.summaryOf(draft)}
            open={shown(section.heading, rejectedAt(pathsIn(section)))}
            disabled={saving}
            onToggle={() => {
              toggleSection(section.heading);
            }}
          >
            <!--
              ROW-ALIGNS-AT-TOP: 上端で揃える。下端で揃えると、誤りの出た欄だけが高くなった分、同じ行の
              他の欄が持ち上がって並びが崩れる（誤りは欄の下に出るため、高さは欄ごとに変わる）。
            -->
            {#each section.rows as row, index (row[0]?.path)}
              <div class="flex flex-wrap items-start gap-4">
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
                  <div class="space-y-1">
                    <!--
                      LABEL-HEIGHT-SPACER: 欄の名の分だけ下げて、入力そのものと同じ高さに置く。上端で
                      揃えているため、これが無いと操作だけが名の行に並ぶ。
                    -->
                    <p class="text-sm font-medium" aria-hidden="true">&nbsp;</p>
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
                  </div>
                {/if}
              </div>
            {/each}
          </AlbumSection>
        {/each}

        <!--
        画像は選んだ時点で送られ、確定できたものだけがここに出る。作品へ反映するのは保存で、
        送るのと反映するのを分けているため、状態の出し方も保存とは別に持つ。
      -->
        <AlbumSection
          heading="カバー画像"
          summary={coverSummary}
          open={shown('カバー画像', coverRejected)}
          disabled={saving}
          onToggle={() => {
            toggleSection('カバー画像');
          }}
        >
          <div class="space-y-4" data-field="coverImageKey">
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
          </div>
        </AlbumSection>
      </fieldset>
    </form>

    <!--
      OUTSIDE-THE-FORM: 反映は上の保存に乗るが、区画は保存のフォームの外に置く。中に置くと、行を足す
      入力とボタンが作品の保存を巻き込む（Enter も submit になる）。
    -->
    <AlbumSection
      heading="曲目"
      summary={tracksSummary}
      open={shown('曲目', tracksRejected)}
      disabled={saving}
      onToggle={() => {
        toggleSection('曲目');
      }}
    >
      <AlbumTracks
        {tracks}
        {rejections}
        disabled={busy}
        messagesOf={messagesAt}
        onEdit={tracksEdited}
        onReposition={tracksRepositioned}
      />

      <!--
        原作の出典は曲目の区画に入れる。「「○○」より各曲」のように<b>曲単位で特定しないまま曲目全体を
        指す一文</b>であり、読む場所も直す場所も曲目の並びの直後になる（#365）。保存は作品の保存に乗る
        ため、`form` 属性でそのフォームへ結び付けている。
      -->
      <fieldset class="space-y-1" data-field={ORIGINAL_WORK_NOTE.path} disabled={saving}>
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
    </AlbumSection>

    <AlbumSection
      heading="外部音源"
      summary={audiosSummary}
      open={shown('外部音源', rejectedUnder(AUDIO_PATH_PREFIX))}
      disabled={saving}
      onToggle={() => {
        toggleSection('外部音源');
      }}
    >
      <AlbumExternalAudios
        {audios}
        disabled={busy}
        messagesOf={audioMessagesOf}
        onEdit={audiosEdited}
        onReposition={audiosRepositioned}
      />
    </AlbumSection>

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
      FIXED-ACTIONS: 保存の操作を画面の下端に留め、未保存であることをそこに出す。

      曲目も外部音源もこの保存に乗るようになり（#391）、畳んだ行の中にも書きかけが残る。操作が画面の
      上端にしか無いと、**書きかけがあること自体が見えない**まま離脱できてしまう。知らせと操作を同じ
      1箇所に置くのは、知らせを読んだ人がその場で保存できるようにするため。

      広い幅では本文の右へ逃がす（#357 の導線と同じ切り替え）。本文は `max-w-2xl` で、器の右には
      収まる空きが残る——本文の上に重ねずに済み、全体を撮った証跡でも欄を覆わない。

      外側の器は `AdminLayout.astro` と同じ幅で組む。`fixed` は viewport からの位置しか持てないため、
      本文の右端を指すには器の位置を再現するしかない。
    -->
    <div class="pointer-events-none fixed inset-x-0 bottom-0 z-10">
      <div class="mx-auto max-w-5xl px-4 lg:max-w-6xl">
        <div
          class="bg-background/95 border-border pointer-events-auto ml-auto flex flex-wrap items-center gap-4 border-t py-3 backdrop-blur lg:w-56 lg:flex-col lg:items-start lg:gap-3 lg:rounded-md lg:border lg:px-4 lg:py-4"
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
    </div>
  </div>
{/if}
