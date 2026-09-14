<script lang="ts">
  import AlbumExternalAudios from '$components/AlbumExternalAudios.svelte';
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import { Button } from '$components/ui/button/index.js';
  import {
    DESCRIPTION_FORMATS,
    EMPTY_DRAFT,
    albumFieldsOf,
    audioDraftsOf,
    draftOf,
    withCleared,
    withValue,
    type AlbumDraft,
    type AlbumFieldPath,
    type ExternalAudioDraft,
  } from '$lib/api/album-form';
  import { uploadAsset } from '$lib/api/asset-upload';
  import {
    createAlbum,
    getAlbum,
    updateAlbum,
    type AdminAlbumDetail,
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
   * トラック・チューン構成の編集は持たない（#122 の別スライス）。外部音源は<b>この保存に乗る</b>——作品の子を
   * 書く経路は集約ルートに1つしかなく（#391）、送った並びがそのまま作品の音源になる。区画が保存のフォームの
   * 外に置かれているのは、行を足す入力が Enter で作品の保存を巻き込まないようにするためである。
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

  /** 入力欄1つの宣言。位置の綴りは管理APIの入力パスと同じ */
  type FieldSpec = Readonly<{
    path: AlbumFieldPath;
    label: string;
    kind: 'text' | 'date' | 'number' | 'multiline' | 'choice';
    /** 選択肢。`choice` 以外では空 */
    choices: readonly string[];
  }>;

  /**
   * 見出しでまとめた入力欄。初出イベントは入れ子の位置（`event.*`）を持つため、まとまりを分ける。
   *
   * <p>
   * `clearing` を持つまとまりは、その欄をまとめて空へ戻す操作を添える。入れ子の項目は1つでも値が
   * 残っていれば送られ、残りの欄が必須として断られるため、外す操作を画面が持たないと利用者が
   * その規則を知っている必要がある。
   * </p>
   */
  type Section = Readonly<{
    heading: string;
    fields: readonly FieldSpec[];
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
      fields: [
        text('title', 'タイトル'),
        { path: 'releaseDate', label: 'リリース日', kind: 'date', choices: [] },
        text('artistDisplayName', 'アーティスト表示名'),
        text('artistSortKey', 'アーティストのソートキー'),
        text('catalogNumber', 'カタログナンバー'),
        text('isdn', 'ISDN'),
        { path: 'description', label: '概要説明', kind: 'multiline', choices: [] },
        {
          path: 'descriptionFormat',
          label: '概要説明の形式',
          kind: 'choice',
          choices: DESCRIPTION_FORMATS,
        },
        text('originalWorkNote', '原作の出典（例:「○○」より各曲）'),
      ],
    },
    {
      heading: '初出イベント',
      fields: [
        text('event.name', 'イベント名'),
        { path: 'event.date', label: '開催日', kind: 'date', choices: [] },
        text('event.place', '会場'),
        text('event.spaceNumber', 'スペース番号'),
        text('event.note', '補足'),
      ],
    },
    {
      heading: '頒布',
      fields: [
        { path: 'basePrice.amount', label: '基準額', kind: 'number', choices: [] },
        text('basePrice.currency', '通貨コード（未指定は円）'),
      ],
      clearing: '基準額を解除',
    },
  ];

  /** 作品本体の欄を持つ位置 */
  const FIELD_PATHS: readonly string[] = SECTIONS.flatMap((section) =>
    section.fields.map((field) => field.path),
  );

  /**
   * 欄を持つ位置。ここに無い位置のエラーは、欄へ割り当てず全体へ出す。
   *
   * <p>
   * 外部音源の位置（`externalAudios[i].url`）は行数で決まるため、いまの入力から組み立てる。行そのものが
   * 無いことを指す位置（`externalAudios[i]`）は欄に対応しないので、そのまま全体のエラーになる
   * （DECISIONS 29）。
   * </p>
   */
  const assignablePaths = $derived<readonly string[]>([
    ...FIELD_PATHS,
    ...audios.map((_, index) => audioPathOf(index)),
  ]);

  /** 外部音源の行を指す位置の接頭辞。行の位置は並びが変われば別の行を指す */
  const AUDIO_PATH_PREFIX = 'externalAudios[';

  /** 外部音源の行の位置。管理APIが検証エラーの `field` として返す綴りと同じ */
  const audioPathOf = (index: number): string => `${AUDIO_PATH_PREFIX}${String(index)}].url`;

  /** 選択肢の表示。値は管理APIの列挙子名で、そのままでは画面に出せない */
  const CHOICE_LABELS: Readonly<Record<string, string>> = {
    PLAIN_TEXT: 'プレーンテキスト',
    MARKDOWN: 'Markdown',
  };

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

  const editing = (apiKey: string, pending: Pending): View => ({
    kind: 'editing',
    apiKey,
    target: pending.target,
    draft: pending.draft,
    coverImageUrl: pending.coverImageUrl,
    audios: pending.audios,
    attempts: 0,
    submission: { kind: 'idle' },
    upload: { kind: 'idle' },
  });

  const lock = (message: string | null, pending: Pending | null): void => {
    view = { kind: 'locked', message, pending };
  };

  const loaded = (apiKey: string, albumId: string, result: ApiResult<AdminAlbumDetail>): View =>
    result.kind === 'ok'
      ? editing(apiKey, {
          target: { albumId, revision: result.value.revision },
          draft: draftOf(result.value),
          coverImageUrl: result.value.coverImageUrl,
          audios: audioDraftsOf(result.value),
        })
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
              section.fields.map((field) => field.path),
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
    draft: AlbumDraft,
    audios: readonly ExternalAudioDraft[],
  ): Promise<ApiResult<unknown>> =>
    target === null
      ? createAlbum(apiKey, albumFieldsOf(draft, audios))
      : updateAlbum(apiKey, target.albumId, albumFieldsOf(draft, audios), target.revision);

  /** いま抱えている入力。編集中でなければ持たない */
  const pendingOf = (current: View): Pending | null =>
    current.kind === 'editing'
      ? {
          target: current.target,
          draft: current.draft,
          coverImageUrl: current.coverImageUrl,
          audios: current.audios,
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
      ? submitWith(current.apiKey, current.target, current.draft, current.audios)
      : Promise.resolve());
  };

  const submitWith = async (
    apiKey: string,
    target: Target | null,
    draft: AlbumDraft,
    audios: readonly ExternalAudioDraft[],
  ): Promise<void> => {
    withSubmission({ kind: 'saving' });
    applySaveOutcome(apiKey, await save(apiKey, target, draft, audios));
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
        ? { ...current, audios, submission: submissionAfterAudioEdit(current.submission) }
        : current;
  };

  /**
   * 外部音源の並びを変えた後の保存の状態。
   *
   * <p>
   * 落とすのは<b>音源の行に割り当てられたエラーだけ</b>。同じ応答には本体の欄の誤り（`title` など）も
   * 一緒に入るため、まとめて捨てると、何も直していない欄のエラーまで消える。
   * </p>
   *
   * <p>
   * 競合や通信断（`conflicted` / `refused`）は入力を変えても消えないため、そのまま残す。
   * </p>
   */
  const submissionAfterAudioEdit = (current: Submission): Submission =>
    current.kind === 'invalid'
      ? invalidOrIdle(withoutPathsUnder(current.errors, AUDIO_PATH_PREFIX))
      : current;

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

  /** その行に割り当てられた誤り */
  const audioMessagesOf = (index: number): readonly string[] =>
    errors.byField.get(audioPathOf(index)) ?? [];

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

  /** 欄の識別子。位置の綴りに含まれる `.` は識別子に使えない */
  const idOf = (path: AlbumFieldPath): string => `album-${path.replace('.', '-')}`;

  /**
   * 1行の入力欄が受け取る型。
   *
   * 複数行・選択肢は別の枝が描くため、ここへは来ない。来ない種別も表へ載せるのは、種別が増えたときに
   * 抜けをコンパイルで気付くため。
   */
  const INPUT_TYPES = {
    text: 'text',
    date: 'date',
    number: 'number',
    multiline: 'text',
    choice: 'text',
  } as const satisfies Record<FieldSpec['kind'], string>;

  const inputTypeOf = (kind: FieldSpec['kind']): string => INPUT_TYPES[kind];

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
    <form class="max-w-2xl space-y-8" onsubmit={submit}>
      <!--
      送ったのはクリックした時点の入力である。保存中も入力を受け付けると、その後の変更は要求に
      入らないまま、成功して一覧へ移ったときに黙って消える。

      画像を送っている最中は塞がない——送り終えても読み直さないので、入力は残る。
    -->
      <fieldset class="space-y-8" disabled={saving}>
        {#each SECTIONS as section (section.heading)}
          <section class="space-y-4">
            <div class="flex items-center justify-between gap-4">
              <h2 class="text-base font-medium">{section.heading}</h2>
              {#if section.clearing !== undefined}
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

            {#each section.fields as field (field.path)}
              <div class="space-y-1" data-field={field.path}>
                <label class="text-sm font-medium" for={idOf(field.path)}>{field.label}</label>

                {#if field.kind === 'choice'}
                  <select
                    id={idOf(field.path)}
                    class="border-input bg-background w-full rounded-md border px-3 py-2"
                    value={draft[field.path]}
                    aria-invalid={messagesOf(field.path).length > 0}
                    onchange={(event) => {
                      update(field.path, event.currentTarget.value);
                    }}
                  >
                    {#each field.choices as choice (choice)}
                      <option value={choice}>{CHOICE_LABELS[choice] ?? choice}</option>
                    {/each}
                  </select>
                {:else if field.kind === 'multiline'}
                  <textarea
                    id={idOf(field.path)}
                    class="border-input bg-background w-full rounded-md border px-3 py-2"
                    rows="4"
                    value={draft[field.path]}
                    aria-invalid={messagesOf(field.path).length > 0}
                    oninput={(event) => {
                      update(field.path, event.currentTarget.value);
                    }}></textarea>
                {:else}
                  <input
                    id={idOf(field.path)}
                    class="border-input bg-background w-full rounded-md border px-3 py-2"
                    type={inputTypeOf(field.kind)}
                    value={draft[field.path]}
                    aria-invalid={messagesOf(field.path).length > 0}
                    oninput={(event) => {
                      update(field.path, event.currentTarget.value);
                    }}
                  />
                {/if}

                {#each messagesOf(field.path) as message (message)}
                  <p class="text-destructive text-sm" role="alert">{message}</p>
                {/each}
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

      <div class="flex items-center gap-4">
        <Button type="submit" disabled={busy}>
          {saving ? '保存しています…' : SAVE_LABELS[mode]}
        </Button>
        <a class="text-sm underline underline-offset-4" href={ALBUM_LIST_PATH}>やめる</a>
      </div>
    </form>

    <!--
      OUTSIDE-THE-FORM: 反映は上の保存に乗るが、区画は保存のフォームの外に置く。中に置くと、行を足す
      入力とボタンが作品の保存を巻き込む（Enter も submit になる）。
    -->
    <AlbumExternalAudios
      {audios}
      disabled={busy}
      messagesOf={audioMessagesOf}
      onChange={audiosChanged}
    />
  </div>
{/if}
