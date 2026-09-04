<script lang="ts">
  import DestructiveConfirmDialog from '$components/DestructiveConfirmDialog.svelte';
  import { Badge } from '$components/ui/badge/index.js';
  import { Button } from '$components/ui/button/index.js';
  import * as Table from '$components/ui/table/index.js';
  import {
    deleteAlbum,
    deletionPreconditions,
    listAlbums,
    publishAlbum,
    unpublicationPreconditions,
    unpublishAlbum,
    type AdminAlbum,
    type ApiResult,
  } from '$lib/api/client';
  import { forgetApiKey, rememberApiKey, storedApiKey } from '$lib/credentials';
  import { formatCalendarDate } from '$lib/format';

  /**
   * 事前確認を要する操作。
   *
   * 削除と非公開化は参照している記事に影響が及ぶ（`docs/ARCHITECTURE.md`）。公開は影響を及ばせない
   * ため、この型に含めず確認を挟まない。
   */
  type DestructiveOperation = 'delete' | 'unpublish';

  /** 対話に並べる、影響を受けるもの1件 */
  type AffectedRow = { readonly key: string; readonly label: string };

  /**
   * 前提の照会1回を同定する印。
   *
   * 世代（順序）ではなく「いま待っている呼び出しか」の等値判定にだけ使う。順序も単調増加も要らない
   * ため可変のカウンタを持たない。取り消して同じ対象を開き直せば別の呼び出しなので、印は一致しない。
   */
  type AskId = symbol;

  /**
   * 確認の対話の状態。
   *
   * 対象と操作は開いている間ずっと必要なため各枝が持つ。
   *
   * **前提を取得できていない（`unavailable`）と、実行が失敗した（`rejected`）を同じ枝にしない。**
   * 前者のやり直し先は前提の照会、後者は操作の実行で、確定できるかどうかも違う。畳むと、影響範囲を
   * 確認できていない状態から実行へ進める経路ができる。
   *
   * `affected` の null は「まだ分かっていない」、空配列は「取得できて影響が無い」を表す。`message` の
   * null は「失敗していない」を表す。
   */
  type Confirmation =
    | { readonly kind: 'closed' }
    | {
        readonly kind: 'asking';
        readonly album: AdminAlbum;
        readonly operation: DestructiveOperation;
        readonly askId: AskId;
        readonly affected: null;
        readonly message: null;
      }
    | {
        readonly kind: 'unavailable';
        readonly album: AdminAlbum;
        readonly operation: DestructiveOperation;
        readonly affected: null;
        readonly message: string;
      }
    | {
        readonly kind: 'ready';
        readonly album: AdminAlbum;
        readonly operation: DestructiveOperation;
        readonly affected: readonly AffectedRow[];
        readonly message: null;
      }
    | {
        readonly kind: 'running';
        readonly album: AdminAlbum;
        readonly operation: DestructiveOperation;
        readonly affected: readonly AffectedRow[];
        readonly message: null;
      }
    | {
        readonly kind: 'rejected';
        readonly album: AdminAlbum;
        readonly operation: DestructiveOperation;
        readonly affected: readonly AffectedRow[];
        readonly message: string;
      };

  /** 確定できる状態。ここからだけ実行へ進む */
  type Confirmable = Extract<Confirmation, { readonly kind: 'ready' | 'rejected' }>;

  /**
   * 画面の状態。
   *
   * 鍵待ち・読み込み中・一覧・失敗を1つの型で表す。個別の真偽値を並べると、読み込み中でありながら
   * 一覧も出ている、といった表せてはいけない組み合わせが作れてしまう。確認の対話は一覧の中に持たせる
   * （一覧が出ていない状態で確認だけ開いている、という組み合わせを作らない）。
   */
  type View =
    | { readonly kind: 'locked'; readonly message: string | null }
    | { readonly kind: 'loading' }
    | {
        readonly kind: 'ready';
        readonly apiKey: string;
        readonly albums: readonly AdminAlbum[];
        readonly confirmation: Confirmation;
      }
    /* 鍵を持ったまま失敗した状態。同じ鍵でやり直せるようにするため、ここで抱える */
    | { readonly kind: 'failed'; readonly message: string; readonly apiKey: string };

  let view = $state<View>({ kind: 'locked', message: null });
  let apiKeyDraft = $state('');

  /*
   * 受け付けられた鍵だけを覚える。断られた鍵を残すと、次に開いたときも同じ失敗から始まる。到達できない
   * だけの失敗では捨てない（鍵の正しさとは別の理由のため）。
   */
  const KEY_STORE = {
    ok: rememberApiKey,
    unauthorized: forgetApiKey,
    failed: rememberApiKey,
  } satisfies Record<ApiResult<unknown>['kind'], (apiKey: string) => void>;

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  /** 失敗の文言。文言の出所を1つにするため、どの操作の失敗もここを通す */
  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  /** 成功なら null、失敗ならその文言 */
  const failureOf = (result: ApiResult<unknown>): string | null =>
    result.kind === 'ok' ? null : failureTextOf(result);

  const toView = (apiKey: string, result: ApiResult<readonly AdminAlbum[]>): View =>
    result.kind === 'ok'
      ? { kind: 'ready', apiKey, albums: result.value, confirmation: { kind: 'closed' } }
      : result.kind === 'unauthorized'
        ? { kind: 'locked', message: '鍵が受け付けられませんでした。' }
        : { kind: 'failed', message: result.message, apiKey };

  const load = async (apiKey: string): Promise<void> => {
    view = { kind: 'loading' };

    const result = await listAlbums(apiKey);
    KEY_STORE[result.kind](apiKey);
    view = toView(apiKey, result);
  };

  /*
   * 覚えている鍵があれば、そのまま読みにいく。このコンポーネントは client:only で載るため、ここが
   * 動くのはブラウザだけになる（組み立ての時点で sessionStorage を触らない）。
   */
  const resume = async (): Promise<void> => {
    const apiKey = storedApiKey();
    return apiKey === null ? undefined : load(apiKey);
  };

  void resume();

  const submit = (event: SubmitEvent): void => {
    event.preventDefault();
    void load(apiKeyDraft);
  };

  /* 同じ鍵でやり直す。到達できないだけの失敗は鍵の正しさとは別のため、入力からやり直させない */
  const retry = (): void => {
    const current = view;
    void (current.kind === 'failed' ? load(current.apiKey) : Promise.resolve());
  };

  const lock = (): void => {
    forgetApiKey();
    view = { kind: 'locked', message: null };
    apiKeyDraft = '';
  };

  /* 確認の対話は一覧の中にあるため、差し替えも一覧の状態を保ったまま行う */
  const withConfirmation = (confirmation: Confirmation): void => {
    const current = view;
    view = current.kind === 'ready' ? { ...current, confirmation } : current;
  };

  const currentConfirmation = (): Confirmation => {
    const current = view;
    return current.kind === 'ready' ? current.confirmation : { kind: 'closed' };
  };

  /** 操作ごとの文言。何を問うているかは操作で変わるため、表で持つ */
  const OPERATION_TEXTS = {
    delete: {
      title: 'この作品を削除しますか',
      description: '削除すると元に戻せません。参照していた記事の参照は失効します。',
      affectedHeading: '影響を受ける記事',
      confirmLabel: '削除する',
    },
    unpublish: {
      title: 'この作品を非公開にしますか',
      description: '非公開にすると、公開サイトから見えなくなります。',
      affectedHeading: '連動して非公開になる記事',
      confirmLabel: '非公開にする',
    },
  } satisfies Record<
    DestructiveOperation,
    {
      readonly title: string;
      readonly description: string;
      readonly affectedHeading: string;
      readonly confirmLabel: string;
    }
  >;

  const toAffectedRow = (article: { readonly articleId: string; readonly title: string }) =>
    ({ key: article.articleId, label: article.title }) satisfies AffectedRow;

  /**
   * 操作ごとの前提の問い合わせ。
   *
   * 返る項目は操作で変わる（削除は失効と非公開化の別、非公開化は連動するものだけ）が、対話が並べるのは
   * 「どれが影響を受けるか」なので、この段で共通の行へ落とす。**どれが影響を受けるかの判定はしない**。
   */
  const ASK_PRECONDITIONS = {
    delete: async (apiKey: string, albumId: string): Promise<ApiResult<readonly AffectedRow[]>> => {
      const result = await deletionPreconditions(apiKey, albumId);
      return result.kind === 'ok' ? { kind: 'ok', value: result.value.map(toAffectedRow) } : result;
    },
    unpublish: async (
      apiKey: string,
      albumId: string,
    ): Promise<ApiResult<readonly AffectedRow[]>> => {
      const result = await unpublicationPreconditions(apiKey, albumId);
      return result.kind === 'ok' ? { kind: 'ok', value: result.value.map(toAffectedRow) } : result;
    },
  } satisfies Record<
    DestructiveOperation,
    (apiKey: string, albumId: string) => Promise<ApiResult<readonly AffectedRow[]>>
  >;

  const RUN_OPERATION = {
    delete: deleteAlbum,
    unpublish: unpublishAlbum,
  } satisfies Record<
    DestructiveOperation,
    (apiKey: string, albumId: string) => Promise<ApiResult<unknown>>
  >;

  const ask = async (album: AdminAlbum, operation: DestructiveOperation): Promise<void> => {
    const current = view;
    return current.kind === 'ready' ? askWith(current.apiKey, album, operation) : undefined;
  };

  const askWith = async (
    apiKey: string,
    album: AdminAlbum,
    operation: DestructiveOperation,
  ): Promise<void> => {
    const askId = Symbol('ask');
    withConfirmation({ kind: 'asking', album, operation, askId, affected: null, message: null });

    const result = await ASK_PRECONDITIONS[operation](apiKey, album.albumId);
    applyAskOutcome(askId, album, operation, result);
  };

  /*
   * STALE-COMPLETION: 対話は問い合わせ中でも閉じられる（返らない応答を待つ間ずっと閉じられないほうが
   * 困る）。閉じた後や別の対象を開いた後に返った応答を捨てるため、呼び出しごとの印で「いま待っている
   * ものか」を見る。作品と操作の一致では足りない——取り消して開き直した2回の照会は、成否も内容も
   * 一致するとは限らないため、古い方が新しい方を上書きし得る。
   */
  const applyAskOutcome = (
    askId: AskId,
    album: AdminAlbum,
    operation: DestructiveOperation,
    result: ApiResult<readonly AffectedRow[]>,
  ): void => {
    const confirmation = currentConfirmation();
    const awaited = confirmation.kind === 'asking' && confirmation.askId === askId;

    withConfirmation(awaited ? askOutcomeOf(album, operation, result) : confirmation);
  };

  const askOutcomeOf = (
    album: AdminAlbum,
    operation: DestructiveOperation,
    result: ApiResult<readonly AffectedRow[]>,
  ): Confirmation =>
    result.kind === 'ok'
      ? { kind: 'ready', album, operation, affected: result.value, message: null }
      : { kind: 'unavailable', album, operation, affected: null, message: failureTextOf(result) };

  /** 確定できる状態からだけ実行へ進む。前提を取得できていない状態からは進めない */
  const CONFIRMABLE_KINDS = ['ready', 'rejected'] as const;

  const confirmableOf = (confirmation: Confirmation): Confirmable | null =>
    CONFIRMABLE_KINDS.some((kind) => kind === confirmation.kind)
      ? (confirmation as Confirmable)
      : null;

  const confirm = async (): Promise<void> => {
    const current = view;
    const confirmable = confirmableOf(currentConfirmation());
    return current.kind === 'ready' && confirmable !== null
      ? runOperation(current.apiKey, confirmable)
      : undefined;
  };

  const runOperation = async (apiKey: string, confirmation: Confirmable): Promise<void> => {
    withConfirmation({ ...confirmation, kind: 'running', message: null });

    const result = await RUN_OPERATION[confirmation.operation](apiKey, confirmation.album.albumId);
    const failure = failureOf(result);

    /* 成功なら一覧を読み直す（`load` が対話を閉じた状態へ戻す）。失敗なら影響一覧を残して再実行させる */
    withConfirmation(
      failure === null
        ? { kind: 'closed' }
        : { ...confirmation, kind: 'rejected', message: failure },
    );
    return failure === null ? load(apiKey) : undefined;
  };

  /*
   * 実行中だけ閉じない（送信の結果を受け取る前に閉じると、成否が伝わらない）。問い合わせ中は閉じられる。
   * 遅れて返った照会は `applyAskOutcome` が捨てるため、閉じた対話が開き直すことはない。
   */
  const cancel = (): void => {
    const confirmation = currentConfirmation();
    withConfirmation(confirmation.kind === 'running' ? confirmation : { kind: 'closed' });
  };

  /** 取得できていない前提を問い直す。やり直し先が実行ではなく照会である状態からだけ呼ぶ */
  const retryPreconditions = async (): Promise<void> => {
    const confirmation = currentConfirmation();
    return confirmation.kind === 'unavailable'
      ? ask(confirmation.album, confirmation.operation)
      : undefined;
  };

  const publish = async (album: AdminAlbum): Promise<void> => {
    const current = view;
    return current.kind === 'ready' ? publishWith(current.apiKey, album) : undefined;
  };

  /* 公開は影響を及ばせないため確認を挟まない。失敗は一覧の失敗として抱える（同じ鍵でやり直せる） */
  const publishWith = async (apiKey: string, album: AdminAlbum): Promise<void> => {
    const result = await publishAlbum(apiKey, album.albumId);
    const failure = failureOf(result);

    view = failure === null ? { kind: 'loading' } : listFailureOf(apiKey, result, failure);
    return failure === null ? load(apiKey) : undefined;
  };

  const listFailureOf = (apiKey: string, result: ApiResult<unknown>, failure: string): View =>
    result.kind === 'unauthorized'
      ? { kind: 'locked', message: failure }
      : { kind: 'failed', message: failure, apiKey };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。テンプレート側で `view.albums` と書くと、型情報を使う検査が解決できない。
   */
  const albums = $derived(view.kind === 'ready' ? view.albums : []);
  const lockMessage = $derived(view.kind === 'locked' ? view.message : null);
  const failureMessage = $derived(view.kind === 'failed' ? view.message : null);
  const confirmation = $derived<Confirmation>(
    view.kind === 'ready' ? view.confirmation : { kind: 'closed' },
  );
  const dialogTexts = $derived(
    confirmation.kind === 'closed' ? null : OPERATION_TEXTS[confirmation.operation],
  );

  /** null は「まだ分かっていない」。空配列（影響なし）と混ぜない */
  const dialogAffected = $derived<readonly AffectedRow[] | null>(
    confirmation.kind === 'closed' ? null : confirmation.affected,
  );
  const dialogFailure = $derived(confirmation.kind === 'closed' ? null : confirmation.message);

  /** 影響が分かっていないときに一覧の代わりに出す文言。状態ごとに理由が違う */
  const PENDING_NOTICES = {
    asking: '影響範囲を確認しています。',
    unavailable: '影響範囲を取得できていません。もう一度確認してください。',
    ready: '',
    running: '',
    rejected: '',
  } satisfies Record<Exclude<Confirmation['kind'], 'closed'>, string>;

  const dialogPendingNotice = $derived(
    confirmation.kind === 'closed' ? '' : PENDING_NOTICES[confirmation.kind],
  );

  /** 確定できるのは、前提を取得できている状態だけ */
  const confirmDisabled = $derived(confirmableOf(confirmation) === null);

  /** 実行中だけ取り消しを塞ぐ */
  const cancelDisabled = $derived(confirmation.kind === 'running');
  const dialogRunning = $derived(confirmation.kind === 'running');

  /** 問い直せるのは、やり直し先が照会である状態だけ */
  const dialogRetry = $derived(
    confirmation.kind === 'unavailable' ? () => void retryPreconditions() : null,
  );

  const stateLabel = (album: AdminAlbum): string =>
    album.publishedAt === null ? '下書き' : '公開';
</script>

{#if view.kind === 'locked'}
  <form class="max-w-md space-y-4" onsubmit={submit}>
    <div class="space-y-1">
      <label class="text-sm font-medium" for="api-key">管理APIの鍵</label>
      <p class="text-muted-foreground text-sm">
        鍵はこのタブを閉じるまで保持します。ビルドには含めません。
      </p>
    </div>

    <input
      id="api-key"
      class="border-input bg-background w-full rounded-md border px-3 py-2"
      type="password"
      autocomplete="off"
      bind:value={apiKeyDraft}
    />

    {#if lockMessage !== null}
      <p class="text-destructive text-sm" role="alert">{lockMessage}</p>
    {/if}

    <Button type="submit">開く</Button>
  </form>
{:else if view.kind === 'loading'}
  <p class="text-muted-foreground">読み込んでいます。</p>
{:else if failureMessage !== null}
  <div class="max-w-md space-y-4">
    <p class="text-destructive" role="alert">{failureMessage}</p>

    <div class="flex items-center gap-4">
      <Button type="button" onclick={retry}>再試行</Button>
      <button class="text-sm underline underline-offset-4" type="button" onclick={lock}>
        鍵を破棄する
      </button>
    </div>
  </div>
{:else}
  <div class="space-y-4">
    <div class="flex items-baseline justify-between">
      <p class="text-muted-foreground text-sm">{albums.length} 件</p>
      <button class="text-sm underline underline-offset-4" type="button" onclick={lock}>
        鍵を破棄する
      </button>
    </div>

    {#if albums.length === 0}
      <p class="text-muted-foreground">登録された作品はありません。</p>
    {:else}
      <Table.Root>
        <Table.Header>
          <Table.Row>
            <Table.Head>タイトル</Table.Head>
            <Table.Head>アーティスト</Table.Head>
            <Table.Head>カタログナンバー</Table.Head>
            <Table.Head>リリース日</Table.Head>
            <Table.Head>状態</Table.Head>
            <Table.Head>操作</Table.Head>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {#each albums as album (album.albumId)}
            <Table.Row>
              <Table.Cell>{album.title}</Table.Cell>
              <Table.Cell>{album.artistDisplayName}</Table.Cell>
              <Table.Cell>{album.catalogNumber ?? '―'}</Table.Cell>
              <Table.Cell>{formatCalendarDate(album.releaseDate)}</Table.Cell>
              <Table.Cell>
                <Badge variant={album.publishedAt === null ? 'secondary' : 'default'}>
                  {stateLabel(album)}
                </Badge>
              </Table.Cell>
              <Table.Cell>
                <div class="flex items-center gap-2">
                  {#if album.publishedAt === null}
                    <Button size="sm" variant="outline" onclick={() => void publish(album)}>
                      公開する
                    </Button>
                  {:else}
                    <Button
                      size="sm"
                      variant="outline"
                      onclick={() => void ask(album, 'unpublish')}
                    >
                      非公開にする
                    </Button>
                  {/if}
                  <Button size="sm" variant="destructive" onclick={() => void ask(album, 'delete')}>
                    削除する
                  </Button>
                </div>
              </Table.Cell>
            </Table.Row>
          {/each}
        </Table.Body>
      </Table.Root>
    {/if}
  </div>

  {#if dialogTexts !== null}
    <DestructiveConfirmDialog
      open={confirmation.kind !== 'closed'}
      title={dialogTexts.title}
      description={dialogTexts.description}
      affectedHeading={dialogTexts.affectedHeading}
      confirmLabel={dialogTexts.confirmLabel}
      affected={dialogAffected}
      pendingNotice={dialogPendingNotice}
      {confirmDisabled}
      {cancelDisabled}
      running={dialogRunning}
      failureMessage={dialogFailure}
      onRetry={dialogRetry}
      onConfirm={() => void confirm()}
      onCancel={cancel}
    />
  {/if}
{/if}
