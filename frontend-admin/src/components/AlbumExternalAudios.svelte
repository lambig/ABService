<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import {
    addExternalAudio,
    removeExternalAudio,
    reorderExternalAudios,
    type AdminExternalAudio,
    type ApiResult,
  } from '$lib/api/client';
  import { KEY_STORE } from '$lib/credentials';

  /**
   * 作品が持つ外部音源。
   *
   * <p>
   * 追加・取り外し・並べ替えは**作品の保存とは別の経路**で、押した時点で反映される。保持するのはURLだけ
   * で、差し替えの操作は持たない（外して足せば表せる）。表示順は常に 1..n の連番で、追加は末尾に付く。
   * </p>
   *
   * <p>
   * 埋め込めるホストかどうかの判定はバックエンドが持つ。画面は入れられたURLをそのまま送り、断られた
   * 理由を出すだけで、送る前に弾かない。
   * </p>
   *
   * <p>
   * どの操作も Album 集約を保存するため、作品の世代が進む。**未保存の入力を抱えたまま操作すると、
   * 読み込んだ世代が古くなり、その後の保存が競合として断られる。** そのため未保存の入力があるときは、
   * 先に作品を保存してよいかを尋ねる。断られたら**保存も音源の操作も行わない**——承諾なしに保存すると、
   * 書きかけの他の欄まで一緒に保存されることになる（更新は全項目置換のため）。
   * </p>
   */
  type Props = {
    readonly apiKey: string;
    /** 対象の作品。まだ作られていなければ null */
    readonly albumId: string | null;
    /** いま持っている外部音源。表示順に並んでいる */
    readonly audios: readonly AdminExternalAudio[];
    /** 作品の入力に未保存の差分があるか。操作の前に保存を尋ねるかどうかを決める */
    readonly dirty: boolean;
    /**
     * 操作の前に作品を保存する。
     *
     * 保存できたときだけ `saved` を返す。断られた場合（入力の誤り・競合・鍵切れ）は `aborted` で、
     * 理由は作品の側に出る。
     */
    readonly saveFirst: () => Promise<'saved' | 'aborted'>;
    /** 操作の最中かどうかを画面全体へ渡す。作品の入力は、その間は触らせない */
    readonly onBusy: (busy: boolean) => void;
    /** 音源が変わったことを画面全体へ渡す。読み直して世代と一覧を取り直す */
    readonly onChanged: () => void;
    /** 鍵が断られたときに画面全体へ渡す。鍵待ちへ戻すかはこの区画では決めない */
    readonly onUnauthorized: (message: string) => void;
  };

  const { apiKey, albumId, audios, dirty, saveFirst, onBusy, onChanged, onUnauthorized }: Props =
    $props();

  /** これから行うこと。保存を尋ねている間、何を保留しているのかを持つ */
  type Pending = Readonly<{
    /** 中止を伝えるときの呼び名 */
    label: string;
    run: (albumId: string) => Promise<ApiResult<unknown>>;
  }>;

  /**
   * 進行中のこと。
   *
   * 中止（`aborted`）と断られた（`rejected`）を分ける。前者は音源へ触れていない、後者は触れたうえで
   * 受け付けられなかった状態で、読み手が次に取る行動が違う。
   */
  type Operation =
    | { readonly kind: 'idle' }
    | { readonly kind: 'confirming'; readonly pending: Pending }
    | { readonly kind: 'running' }
    | { readonly kind: 'aborted'; readonly message: string }
    | { readonly kind: 'rejected'; readonly message: string };

  let operation = $state<Operation>({ kind: 'idle' });
  let url = $state('');

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  /**
   * 鍵が断られたかどうかで、画面全体へ渡すかを分ける。
   *
   * 鍵が断られたのはこの区画だけの問題ではない。ここへ理由を留めると、鍵の入力へ戻れないまま何度
   * やり直しても断られる。
   */
  const ESCALATE = {
    ok: (): void => undefined,
    unauthorized: (message: string): void => {
      onUnauthorized(message);
    },
    failed: (): void => undefined,
  } satisfies Record<ApiResult<unknown>['kind'], (message: string) => void>;

  const settle = (result: ApiResult<unknown>): void => {
    const message = result.kind === 'ok' ? '' : failureTextOf(result);

    KEY_STORE[result.kind](apiKey);
    operation = result.kind === 'ok' ? { kind: 'idle' } : { kind: 'rejected', message };
    ESCALATE[result.kind](message);

    const notify = result.kind === 'ok' ? onChanged : (): void => undefined;
    notify();
  };

  const perform = async (id: string, pending: Pending): Promise<void> => {
    operation = { kind: 'running' };
    onBusy(true);
    settle(await pending.run(id));
    onBusy(false);
  };

  /**
   * 保存してから行う。
   *
   * 保存が通らなければ音源へは触れない。作品の側に理由が出るため、ここには**行わなかったこと**だけを残す
   * ——保存の失敗だけが見えると、押した操作が実行されたのかどうかが読めない。
   */
  const abort = (pending: Pending, reason: string): void => {
    operation = {
      kind: 'aborted',
      message: `作品を${reason}ため、${pending.label}は行いませんでした。`,
    };
    onBusy(false);
  };

  /** 保存の結果ごとの続け方。保存できなければ音源へは触れない */
  const PREPARED = {
    saved: (id: string, pending: Pending): Promise<void> => perform(id, pending),
    aborted: (id: string, pending: Pending): Promise<void> => {
      abort(pending, '保存できなかった');
      return Promise.resolve();
    },
  } satisfies Record<'saved' | 'aborted', (id: string, pending: Pending) => Promise<void>>;

  const saveThenPerform = async (id: string, pending: Pending): Promise<void> => {
    operation = { kind: 'running' };
    onBusy(true);

    return PREPARED[await saveFirst()](id, pending);
  };

  /** 未保存の差分があるかで、尋ねるか、そのまま行うかを分ける */
  const startWith = (id: string, pending: Pending): void => {
    operation = dirty ? { kind: 'confirming', pending } : operation;

    void (dirty ? Promise.resolve() : perform(id, pending));
  };

  /** 作品がまだ無ければ何もしない（区画も操作を出していない） */
  const start = (label: string, run: Pending['run']): void => {
    const id = albumId;
    const begin = (target: string): void => {
      startWith(target, { label, run });
    };

    [id].filter((target) => target !== null).forEach(begin);
  };

  const confirmSave = (): void => {
    const current = operation;
    const id = albumId;

    void (current.kind === 'confirming' && id !== null
      ? saveThenPerform(id, current.pending)
      : Promise.resolve());
  };

  const cancelSave = (): void => {
    const current = operation;

    operation =
      current.kind === 'confirming'
        ? {
            kind: 'aborted',
            message: `作品を保存していないため、${current.pending.label}は行いませんでした。`,
          }
        : current;
  };

  const add = (event: SubmitEvent): void => {
    event.preventDefault();
    start('追加', (id) => addExternalAudio(apiKey, id, url));
  };

  const remove = (audio: AdminExternalAudio): void => {
    start('取り外し', (id) => removeExternalAudio(apiKey, id, audio.externalAudioId));
  };

  /** 入れ替えた並び。並べ替えは全件を1件ずつ含む必要がある */
  const swapped = (index: number, other: number): readonly string[] =>
    audios.map((audio, position) =>
      position === index
        ? (audios[other]?.externalAudioId ?? audio.externalAudioId)
        : position === other
          ? (audios[index]?.externalAudioId ?? audio.externalAudioId)
          : audio.externalAudioId,
    );

  const move = (index: number, other: number): void => {
    start('並べ替え', (id) => reorderExternalAudios(apiKey, id, swapped(index, other)));
  };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。
   */
  const confirmingLabel = $derived(
    operation.kind === 'confirming' ? operation.pending.label : null,
  );
  const running = $derived(operation.kind === 'running');
  const abortedMessage = $derived(operation.kind === 'aborted' ? operation.message : null);
  const rejectedMessage = $derived(operation.kind === 'rejected' ? operation.message : null);

  /** 尋ねている間も操作を受け付けない。返事の前に別の操作が走ると、尋ねた対象が入れ替わる */
  const busy = $derived([running, confirmingLabel !== null].some(Boolean));
  const addDisabled = $derived([busy, url === ''].some(Boolean));
  const last = $derived(audios.length - 1);
</script>

<section class="max-w-2xl space-y-4">
  <h2 class="text-base font-medium">外部音源</h2>
  <p class="text-muted-foreground text-sm">
    追加・取り外し・並べ替えは押した時点で反映されます（作品の保存とは別の操作です）。
  </p>

  {#if albumId === null}
    <p class="text-muted-foreground text-sm">作品を作成すると、外部音源を追加できます。</p>
  {:else}
    {#if audios.length === 0}
      <p class="text-muted-foreground text-sm">外部音源はありません。</p>
    {:else}
      <ul class="space-y-1" data-external-audios>
        {#each audios as audio, index (audio.externalAudioId)}
          <li class="flex items-center gap-2">
            <span class="text-muted-foreground w-6 text-sm">{audio.displayOrder}</span>
            <span class="min-w-0 flex-1 truncate text-sm">{audio.url}</span>

            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={[busy, index === 0].some(Boolean)}
              onclick={() => {
                move(index, index - 1);
              }}
            >
              上へ
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={[busy, index === last].some(Boolean)}
              onclick={() => {
                move(index, index + 1);
              }}
            >
              下へ
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={busy}
              onclick={() => {
                remove(audio);
              }}
            >
              外す
            </Button>
          </li>
        {/each}
      </ul>
    {/if}

    <!--
      この区画は作品の保存のフォームの外に置かれる（画面全体がそう組んでいる）。したがってここで
      フォームを持てる。Enter で追加できるのは、作品の保存を巻き込まないためでもある。
    -->
    <form class="flex flex-wrap items-end gap-2" onsubmit={add}>
      <div class="min-w-0 flex-1 space-y-1">
        <label class="text-sm font-medium" for="album-external-audio-url">音源のURL</label>
        <input
          id="album-external-audio-url"
          class="border-input bg-background w-full rounded-md border px-3 py-2"
          type="text"
          bind:value={url}
          disabled={busy}
        />
      </div>
      <Button type="submit" size="sm" variant="outline" disabled={addDisabled}>追加する</Button>
    </form>

    <!--
      尋ねるのは、承諾なしに保存しないため。対話ではなくこの場に出すのは、見せるものが無いからで
      ある（削除の確認は影響範囲を伴うが、ここで示すのは「先に保存する」という手順だけ）。
    -->
    {#if confirmingLabel !== null}
      <div class="border-input space-y-2 rounded-md border p-3" role="alert">
        <p class="text-sm">
          保存していない入力があります。{confirmingLabel}の前に、作品を保存します。
        </p>
        <p class="text-muted-foreground text-sm">
          保存すると、いま入力している内容がそのまま作品へ反映されます。
        </p>
        <div class="flex items-center gap-2">
          <Button type="button" size="sm" onclick={confirmSave}>保存して続ける</Button>
          <Button type="button" size="sm" variant="outline" onclick={cancelSave}>やめる</Button>
        </div>
      </div>
    {/if}

    {#if running}
      <p class="text-muted-foreground text-sm">実行しています…</p>
    {/if}

    {#if abortedMessage !== null}
      <p class="text-muted-foreground text-sm" role="status">{abortedMessage}</p>
    {/if}

    {#if rejectedMessage !== null}
      <p class="text-destructive text-sm" role="alert">{rejectedMessage}</p>
    {/if}
  {/if}
</section>
