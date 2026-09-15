<script lang="ts">
  import SessionControls from '$components/SessionControls.svelte';
  import ApiKeyForm from '$components/ApiKeyForm.svelte';
  import { Button } from '$components/ui/button/index.js';
  import * as Table from '$components/ui/table/index.js';
  import {
    listSiteContents,
    upsertSiteContent,
    type ApiResult,
    type SiteContent,
  } from '$lib/api/client';
  import {
    NO_ERRORS,
    formErrorsOf,
    hasAssignedErrors,
    type FormErrors,
  } from '$lib/api/form-errors';
  import { applySessionResult, storedSession, type AdminSession } from '$lib/credentials';
  import { renderBody } from '$lib/markup';
  import { unregisteredKeys } from '$lib/site-content-keys';

  /**
   * サイトの文言の一覧と編集（#383）。
   *
   * <p>
   * #230 は「初期データは持たない、管理画面から入れる」と決めたが、その入り口が無かった。ここが
   * その口になる。扱うのは公開サイトが読む文言すべてで、フッターのコピーライト（#343）もその1つ。
   * </p>
   *
   * <p>
   * **キーは自由文字列で、画面が閉じない。** 候補は出すが選択肢にはしない——閉じると、文言を1つ
   * 増やすのにデプロイが要る（#230 が自由キーを選んだ理由がそれ）。形式の検証はバックエンドが持ち、
   * 返った位置（`key` / `content` / `contentFormat`）を欄へ割り当てる（DECISIONS 29）。
   * </p>
   *
   * <p>
   * 保存は世代を送らない。文言は「そのキーの現在の内容」で履歴を持たず、最後の保存が残る契約である
   * （作品・記事の #287 とはここが違う）。したがって編集中に別の保存が入っても競合として断られない。
   * </p>
   *
   * <p>
   * 削除は持たない。バックエンドにも経路が無く、画面だけが持つことはできない。
   * </p>
   */

  /** 編集中の1件。登録済みを直しているときはキーを変えられない */
  type Draft = Readonly<{
    key: string;
    content: string;
    contentFormat: string;
    /**
     * 既に登録されているキーを直しているか。
     *
     * <p>
     * 登録済みのキーを書き換えられるようにすると、**別のキーとして新しく作られる**（保存は upsert で、
     * 名前を変える操作ではない）。消す経路も無いため、書き間違いがそのまま残る。
     * </p>
     */
    registered: boolean;
  }>;

  /** 形式の選択肢。値は管理APIの列挙子名 */
  const CONTENT_FORMATS = ['PLAIN_TEXT', 'MARKDOWN'] as const;

  /** 選択肢の表示。列挙子名はそのままでは画面に出せない */
  const FORMAT_LABELS: Readonly<Record<string, string>> = {
    PLAIN_TEXT: 'プレーンテキスト',
    MARKDOWN: 'Markdown',
  };

  const EMPTY_DRAFT: Draft = {
    key: '',
    content: '',
    contentFormat: 'PLAIN_TEXT',
    registered: false,
  };

  /** 欄を持つ位置。ここに無い位置のエラーは、欄へ割り当てず全体へ出す */
  const ASSIGNABLE_PATHS: readonly string[] = ['key', 'content', 'contentFormat'];

  /**
   * 保存の状態。
   *
   * 検証で拒まれた（`invalid`）と、それ以外の理由で保存できなかった（`refused`）を分ける。直す先が
   * 前者は入力にあり、後者（通信断・応答の契約違反）は入力を直しても変わらない。
   */
  type Submission =
    | { readonly kind: 'idle' }
    | { readonly kind: 'saving' }
    | { readonly kind: 'invalid'; readonly errors: FormErrors }
    | { readonly kind: 'refused'; readonly message: string }
    | { readonly kind: 'saved'; readonly key: string };

  /**
   * 画面の状態。
   *
   * 入力値を持つのは編集中だけ。読み込みに失敗した状態で入力欄が出ることを作らない。
   */
  type View =
    | {
        readonly kind: 'locked';
        readonly message: string | null;
        /** 鍵を入れ直したら戻る入力。まだ入力が無いときは null */
        readonly pending: Draft | null;
      }
    | { readonly kind: 'loading' }
    | { readonly kind: 'unavailable'; readonly session: AdminSession; readonly message: string }
    | {
        readonly kind: 'editing';
        readonly session: AdminSession;
        readonly contents: readonly SiteContent[];
        readonly draft: Draft;
        readonly submission: Submission;
      };

  let view = $state<View>({ kind: 'loading' });

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  /** 失敗の文言。文言の出所を1つにするため、どの操作の失敗もここを通す */
  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized'
      ? 'セッションが終了しました。鍵を入力して再認証してください。'
      : failure.message;

  const editing = (
    session: AdminSession,
    contents: readonly SiteContent[],
    draft: Draft,
  ): View => ({
    kind: 'editing',
    session,
    contents,
    draft,
    submission: { kind: 'idle' },
  });

  const loaded = (
    session: AdminSession,
    result: ApiResult<readonly SiteContent[]>,
    draft: Draft,
  ): View =>
    result.kind === 'ok'
      ? editing(session, result.value, draft)
      : result.kind === 'unauthorized'
        ? { kind: 'locked', message: failureTextOf(result), pending: null }
        : { kind: 'unavailable', session, message: failureTextOf(result) };

  const load = async (session: AdminSession, draft: Draft): Promise<void> => {
    view = { kind: 'loading' };

    const result = await listSiteContents(session);
    return applySessionResult(session, result, () => {
      view = loaded(session, result, draft);
    });
  };

  /*
   * このコンポーネントは client:only で載るため、ここが動くのはブラウザだけになる（組み立ての時点で
   * sessionStorage を触らない）。
   */
  const resume = (): Promise<void> => {
    const session = storedSession();

    return session === null
      ? Promise.resolve().then(() => {
          view = { kind: 'locked', message: null, pending: null };
        })
      : load(session, EMPTY_DRAFT);
  };

  void resume();

  /**
   * 鍵を受け取ったときの続け方。
   *
   * 入力を抱えたまま鍵待ちへ戻っていれば、その入力を持ったまま読み直す。捨てると、鍵が断られる前に
   * 書いていた内容が消える。
   */
  const accept = (session: AdminSession): Promise<void> => {
    const current = view;
    const pending = current.kind === 'locked' ? current.pending : null;

    return load(session, pending ?? EMPTY_DRAFT);
  };

  const retry = (): void => {
    const current = view;
    void (current.kind === 'unavailable' ? load(current.session, EMPTY_DRAFT) : Promise.resolve());
  };

  /** 入力値だけを差し替えた画面。保存の状態は最初へ戻す（前の保存の結果を新しい入力へ引き継がない） */
  const withDraft = (draft: Draft): void => {
    const current = view;
    view = current.kind === 'editing' ? editing(current.session, current.contents, draft) : current;
  };

  const update = (change: Partial<Draft>): void => {
    const current = view;
    view =
      current.kind === 'editing' ? { ...current, draft: { ...current.draft, ...change } } : current;
  };

  /** 登録済みの1件を編集へ移す */
  const edit = (content: SiteContent): void => {
    withDraft({
      key: content.key,
      content: content.content,
      contentFormat: content.contentFormat,
      registered: true,
    });
  };

  /** 新しいキーを足す。キーを入力できる状態から始める */
  const addNew = (key: string): void => {
    withDraft({ ...EMPTY_DRAFT, key });
  };

  const withSubmissionOf = (submission: Submission): View => {
    const current = view;
    return current.kind === 'editing' ? { ...current, submission } : current;
  };

  const withSubmission = (submission: Submission): void => {
    view = withSubmissionOf(submission);
  };

  /**
   * 保存できた文言を一覧へ入れる。
   *
   * 引き直さないのは、保存の応答が保存後の内容そのものを返すため。引き直すと、同じものをもう一度
   * 取りに行ったうえ、その間に別の保存が入れば画面が飛ぶ。
   */
  const merged = (contents: readonly SiteContent[], saved: SiteContent): readonly SiteContent[] =>
    contents.some((content) => content.key === saved.key)
      ? contents.map((content) => (content.key === saved.key ? saved : content))
      : [...contents, saved].toSorted((left, right) => left.key.localeCompare(right.key));

  const withSaved = (saved: SiteContent): View => {
    const current = view;
    return current.kind === 'editing'
      ? {
          ...current,
          contents: merged(current.contents, saved),
          /* 保存した先は登録済みになる。続けて直せるよう、入力はそのまま残す */
          draft: { ...current.draft, registered: true },
          submission: { kind: 'saved', key: saved.key },
        }
      : current;
  };

  /**
   * 保存できなかった結果を、直す先で分ける。
   *
   * 欄へ割り当てられる位置が1つでもあれば検証エラーとして扱う。位置が無い 400（要求全体に関わるもの）は
   * 全体のエラーとして出す枝へ渡る。
   */
  const rejectionOf = (failure: ApiFailure): Submission => {
    const errors = formErrorsOf(failure.problem, ASSIGNABLE_PATHS);

    return [hasAssignedErrors(errors), errors.unassigned.length > 0].some(Boolean)
      ? { kind: 'invalid', errors }
      : { kind: 'refused', message: failureTextOf(failure) };
  };

  /** いま抱えている入力。編集中でなければ持たない */
  const pendingOf = (current: View): Draft | null =>
    current.kind === 'editing' ? current.draft : null;

  const viewAfterFailure = (failure: ApiFailure): View =>
    failure.kind === 'unauthorized'
      ? { kind: 'locked', message: failureTextOf(failure), pending: pendingOf(view) }
      : withSubmissionOf(rejectionOf(failure));

  const applySaveOutcome = (session: AdminSession, result: ApiResult<SiteContent>): void => {
    return applySessionResult(session, result, () => {
      view = result.kind === 'ok' ? withSaved(result.value) : viewAfterFailure(result);
    });
  };

  const submitWith = async (session: AdminSession, draft: Draft): Promise<void> => {
    withSubmission({ kind: 'saving' });
    applySaveOutcome(
      session,
      await upsertSiteContent(session, draft.key, draft.content, draft.contentFormat),
    );
  };

  const submit = (event: SubmitEvent): void => {
    event.preventDefault();

    const current = view;
    void (current.kind === 'editing' && current.submission.kind !== 'saving'
      ? submitWith(current.session, current.draft)
      : Promise.resolve());
  };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。
   */
  const lockMessage = $derived(view.kind === 'locked' ? view.message : null);
  const pendingNotice = $derived(
    view.kind === 'locked' && view.pending !== null
      ? '入力した内容は保持しています。鍵を入れ直すと、続けて保存できます。'
      : null,
  );
  const unavailableMessage = $derived(view.kind === 'unavailable' ? view.message : null);
  const contents = $derived<readonly SiteContent[]>(view.kind === 'editing' ? view.contents : []);
  const draft = $derived<Draft>(view.kind === 'editing' ? view.draft : EMPTY_DRAFT);
  const submission = $derived<Submission>(
    view.kind === 'editing' ? view.submission : { kind: 'idle' },
  );
  const errors = $derived<FormErrors>(
    submission.kind === 'invalid' ? submission.errors : NO_ERRORS,
  );
  const refusedMessage = $derived(submission.kind === 'refused' ? submission.message : null);
  const savedKey = $derived(submission.kind === 'saved' ? submission.key : null);
  const saving = $derived(submission.kind === 'saving');

  /** まだ登録されていない候補。運営者が公開サイトの実装を読まずに済むように出す */
  const candidates = $derived(unregisteredKeys(contents.map((content) => content.key)));

  const messagesOf = (path: string): readonly string[] => errors.byField.get(path) ?? [];

  /**
   * 本文の描画結果。
   *
   * 描画は公開サイトと同じ共有の関数を通す（DECISIONS 24）。ここで別の描画を持つと、プレビューが
   * 嘘になる。プレーンテキストは記法として解釈しないため、この値は null になる。
   */
  const previewHtml = $derived(
    draft.contentFormat === 'MARKDOWN' ? renderBody(draft.content) : null,
  );
</script>

<SessionControls
  onLogout={() => {
    view = { kind: 'locked', message: null, pending: null };
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
{:else if unavailableMessage !== null}
  <div class="max-w-md space-y-4">
    <p class="text-destructive" role="alert">{unavailableMessage}</p>
    <Button type="button" onclick={retry}>再試行</Button>
  </div>
{:else}
  <div class="space-y-8">
    <section class="space-y-4">
      <h2 class="text-base font-medium">登録済みの文言</h2>

      {#if contents.length === 0}
        <p class="text-muted-foreground text-sm">まだ1つも登録されていません。</p>
      {:else}
        <Table.Root>
          <Table.Header>
            <Table.Row>
              <Table.Head>キー</Table.Head>
              <Table.Head>形式</Table.Head>
              <Table.Head>本文</Table.Head>
              <Table.Head>操作</Table.Head>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {#each contents as content (content.key)}
              <Table.Row>
                <Table.Cell class="whitespace-normal">{content.key}</Table.Cell>
                <Table.Cell
                  >{FORMAT_LABELS[content.contentFormat] ?? content.contentFormat}</Table.Cell
                >
                <!--
                  本文は長い。折り返さず1行に抑え、全文は編集の欄で読む（行の高さが揃わないと、
                  操作の位置が行ごとにずれる。#345）。
                -->
                <Table.Cell class="max-w-xs truncate">{content.content}</Table.Cell>
                <Table.Cell>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onclick={() => {
                      edit(content);
                    }}
                  >
                    編集する
                  </Button>
                </Table.Cell>
              </Table.Row>
            {/each}
          </Table.Body>
        </Table.Root>
      {/if}
    </section>

    {#if candidates.length > 0}
      <!--
        候補は選択肢ではない。キーは自由文字列のままで（#230）、ここに無いキーも入れられる。
        出す理由は、運営者が公開サイトの実装を読まずに「何を入れる場所か」を知れるようにするため。
      -->
      <section class="space-y-2" data-candidates>
        <h2 class="text-base font-medium">公開サイトが読む、まだ入っていないキー</h2>
        <ul class="space-y-1 text-sm">
          {#each candidates as candidate (candidate.key)}
            <li class="flex items-baseline gap-3">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onclick={() => {
                  addNew(candidate.key);
                }}
              >
                {candidate.key}
              </Button>
              <span class="text-muted-foreground">{candidate.where}</span>
            </li>
          {/each}
        </ul>
      </section>
    {/if}

    <form class="max-w-2xl space-y-4" onsubmit={submit}>
      <div class="flex items-center justify-between gap-4">
        <h2 class="text-base font-medium">
          {draft.registered ? '文言を直す' : '文言を足す'}
        </h2>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onclick={() => {
            addNew('');
          }}
        >
          新しいキーにする
        </Button>
      </div>

      <fieldset class="space-y-4" disabled={saving}>
        <div class="space-y-1" data-field="key">
          <label class="text-sm font-medium" for="site-content-key">キー</label>
          <!--
            登録済みのキーは変えられない。書き換えると別のキーとして新しく作られ（保存は upsert）、
            消す経路も無いため、書き間違いがそのまま残る。
          -->
          <input
            id="site-content-key"
            class="border-input bg-background w-full rounded-md border px-3 py-2"
            type="text"
            value={draft.key}
            readonly={draft.registered}
            aria-invalid={messagesOf('key').length > 0}
            oninput={(event) => {
              update({ key: event.currentTarget.value });
            }}
          />
          {#each messagesOf('key') as message (message)}
            <p class="text-destructive text-sm" role="alert">{message}</p>
          {/each}
        </div>

        <div class="space-y-1" data-field="contentFormat">
          <label class="text-sm font-medium" for="site-content-format">形式</label>
          <select
            id="site-content-format"
            class="border-input bg-background w-full rounded-md border px-3 py-2"
            value={draft.contentFormat}
            aria-invalid={messagesOf('contentFormat').length > 0}
            onchange={(event) => {
              update({ contentFormat: event.currentTarget.value });
            }}
          >
            {#each CONTENT_FORMATS as format (format)}
              <option value={format}>{FORMAT_LABELS[format] ?? format}</option>
            {/each}
          </select>
          {#each messagesOf('contentFormat') as message (message)}
            <p class="text-destructive text-sm" role="alert">{message}</p>
          {/each}
        </div>

        <div class="space-y-1" data-field="content">
          <label class="text-sm font-medium" for="site-content-content">本文</label>
          <textarea
            id="site-content-content"
            class="border-input bg-background w-full rounded-md border px-3 py-2"
            rows="6"
            value={draft.content}
            aria-invalid={messagesOf('content').length > 0}
            oninput={(event) => {
              update({ content: event.currentTarget.value });
            }}></textarea>
          {#each messagesOf('content') as message (message)}
            <p class="text-destructive text-sm" role="alert">{message}</p>
          {/each}
        </div>
      </fieldset>

      {#if previewHtml !== null}
        <section class="space-y-2" data-preview>
          <h3 class="text-sm font-medium">プレビュー</h3>
          <!--
            描画は公開サイトと同じ共有の関数を通す（DECISIONS 24）。サニタイズ済みの HTML を受け取る
            契約のため、そのまま差し込む。
          -->
          <div class="markup border-border rounded-md border p-4">
            <!-- eslint-disable-next-line svelte/no-at-html-tags -- 描画は共有の関数がサニタイズ済みの HTML を返す契約（DECISIONS 24） -->
            {@html previewHtml}
          </div>
        </section>
      {/if}

      {#if errors.unassigned.length > 0}
        <section class="space-y-1">
          <h3 class="text-sm font-medium">どの項目にも紐付かないエラー</h3>
          {#each errors.unassigned as message (message)}
            <p class="text-destructive text-sm" role="alert">{message}</p>
          {/each}
        </section>
      {/if}

      {#if refusedMessage !== null}
        <p class="text-destructive text-sm" role="alert">{refusedMessage}</p>
      {/if}

      {#if savedKey !== null}
        <p class="text-muted-foreground text-sm" role="status">{savedKey} を保存しました。</p>
      {/if}

      <Button type="submit" disabled={saving}>
        {saving ? '保存しています…' : '保存する'}
      </Button>
    </form>
  </div>
{/if}
