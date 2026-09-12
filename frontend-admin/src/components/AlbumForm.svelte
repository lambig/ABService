<script lang="ts">
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import { Button } from '$components/ui/button/index.js';
  import {
    DESCRIPTION_FORMATS,
    EMPTY_DRAFT,
    albumFieldsOf,
    draftOf,
    withValue,
    type AlbumDraft,
    type AlbumFieldPath,
  } from '$lib/api/album-form';
  import {
    createAlbum,
    getAlbum,
    updateAlbum,
    type AdminAlbumDetail,
    type ApiResult,
  } from '$lib/api/client';
  import {
    NO_ERRORS,
    formErrorsOf,
    hasAssignedErrors,
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
   * トラック・チューン構成・外部音源・カバー画像の編集は持たない（#122 の別スライス）。カバー画像の
   * 鍵は読み込んだ値をそのまま送り返す（更新は全項目置換のため、送らないと画像を外す指定になる）。
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

  /** 見出しでまとめた入力欄。初出イベントは入れ子の位置（`event.*`）を持つため、まとまりを分ける */
  type Section = Readonly<{ heading: string; fields: readonly FieldSpec[] }>;

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
    },
  ];

  /** 欄を持つ位置。ここに無い位置のエラーは、欄へ割り当てず全体へ出す */
  const ASSIGNABLE_PATHS: readonly string[] = SECTIONS.flatMap((section) =>
    section.fields.map((field) => field.path),
  );

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
  type Pending = Readonly<{ target: Target | null; draft: AlbumDraft }>;

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
        readonly submission: Submission;
      };

  let view = $state<View>({ kind: 'loading' });

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  /** 失敗の文言。文言の出所を1つにするため、どの操作の失敗もここを通す */
  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  const editing = (apiKey: string, target: Target | null, draft: AlbumDraft): View => ({
    kind: 'editing',
    apiKey,
    target,
    draft,
    submission: { kind: 'idle' },
  });

  const lock = (message: string | null, pending: Pending | null): void => {
    view = { kind: 'locked', message, pending };
  };

  const loaded = (apiKey: string, albumId: string, result: ApiResult<AdminAlbumDetail>): View =>
    result.kind === 'ok'
      ? editing(apiKey, { albumId, revision: result.value.revision }, draftOf(result.value))
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
    view = editing(apiKey, null, EMPTY_DRAFT);
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
          view = editing(apiKey, pending.target, pending.draft);
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

  /** 競合として返る状態コード。編集を始めた後に別の操作が保存している（#287） */
  const CONFLICT_STATUS = 409;

  /**
   * 競合は入力の誤りと分けて扱う。
   *
   * 直す先が入力ではなく「読み直し」にあるため、欄へも全体のエラーへも出さない。古い値を自動で再送も
   * しない（同じ世代で送り直せば再び競合する）。
   */
  const rejectionOf = (failure: ApiFailure): Submission =>
    failure.status === CONFLICT_STATUS
      ? { kind: 'conflicted' }
      : rejection(failure, formErrorsOf(failure.problem, ASSIGNABLE_PATHS));

  /**
   * 保存の経路。
   *
   * 更新は編集を始めた時点の世代を条件として送る。新規作成に世代は無い（まだ無いものは誰も更新できない）。
   */
  const save = async (
    apiKey: string,
    target: Target | null,
    draft: AlbumDraft,
  ): Promise<ApiResult<unknown>> =>
    target === null
      ? createAlbum(apiKey, albumFieldsOf(draft))
      : updateAlbum(apiKey, target.albumId, albumFieldsOf(draft), target.revision);

  /** いま抱えている入力。編集中でなければ持たない */
  const pendingOf = (current: View): Pending | null =>
    current.kind === 'editing' ? { target: current.target, draft: current.draft } : null;

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
    void (current.kind === 'editing' && current.submission.kind !== 'saving'
      ? submitWith(current.apiKey, current.target, current.draft)
      : Promise.resolve());
  };

  const submitWith = async (
    apiKey: string,
    target: Target | null,
    draft: AlbumDraft,
  ): Promise<void> => {
    withSubmission({ kind: 'saving' });
    applySaveOutcome(apiKey, await save(apiKey, target, draft));
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
  <form class="max-w-2xl space-y-8" onsubmit={submit}>
    <!--
      送ったのはクリックした時点の入力である。保存中も入力を受け付けると、その後の変更は要求に
      入らないまま、成功して一覧へ移ったときに黙って消える。
    -->
    <fieldset class="space-y-8" disabled={saving}>
      {#each SECTIONS as section (section.heading)}
        <section class="space-y-4">
          <h2 class="text-base font-medium">{section.heading}</h2>

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
      <Button type="submit" disabled={saving}>
        {saving ? '保存しています…' : SAVE_LABELS[mode]}
      </Button>
      <a class="text-sm underline underline-offset-4" href={ALBUM_LIST_PATH}>やめる</a>
    </div>
  </form>
{/if}
