<script lang="ts">
  import { Badge } from '$components/ui/badge/index.js';
  import { Button } from '$components/ui/button/index.js';
  import {
    addArticleTag,
    listArticleTags,
    removeArticleTag,
    type AdminArticleTag,
    type ApiResult,
  } from '$lib/api/client';

  /**
   * 記事のタグ。
   *
   * <p>
   * **本文の保存とは別の経路**で、押した時点で反映される（`UpdateArticleRequest` はタグを持たず、
   * 編集開始時点の世代も要らない）。同じ画面に「保存して初めて効く入力」と「押すと効く操作」が
   * 並ぶため、区画を分けてその旨を書く。
   * </p>
   *
   * <p>
   * 付けるのは既にある名前から選ぶ。同名かどうかの判定はバックエンドが持ち（DECISIONS 23）、画面は
   * 正規化も重複の判定もしない。
   * </p>
   *
   * <p>
   * 付いているタグ自体はこの区画で持たず、画面全体から受け取って変更を返す。写しを持つと、鍵を
   * 入れ直した後に戻る先が付け外しの前の状態になる。
   * </p>
   */
  type Props = {
    readonly apiKey: string;
    /** 対象の記事。まだ作られていなければ null */
    readonly articleId: string | null;
    /** 付いているタグ */
    readonly tags: readonly AdminArticleTag[];
    /** 鍵が断られたときに画面全体へ渡す。鍵待ちへ戻すかはこの区画では決めない */
    readonly onUnauthorized: (message: string) => void;
    /** 付け外しの結果を画面全体へ渡す */
    readonly onChanged: (tags: readonly AdminArticleTag[]) => void;
  };

  const { apiKey, articleId, tags, onUnauthorized, onChanged }: Props = $props();

  /**
   * 選べるタグの読み込み。
   *
   * 候補を出せない状態と、候補が0件の状態を混ぜない。前者は読み直せる。
   */
  type Candidates =
    | { readonly kind: 'loading' }
    | { readonly kind: 'ready'; readonly items: readonly AdminArticleTag[] }
    | { readonly kind: 'unavailable'; readonly message: string };

  /**
   * 進行中のこと。
   *
   * 1つずつしか受け付けない（連打で同じタグを二重に送らない）。`rejected` は直前の操作が断られた理由で、
   * そこから同じ操作をやり直せる。
   */
  type Operation =
    | { readonly kind: 'idle' }
    | { readonly kind: 'adding' }
    | { readonly kind: 'removing' }
    | { readonly kind: 'rejected'; readonly message: string };

  let candidates = $state<Candidates>({ kind: 'loading' });
  let operation = $state<Operation>({ kind: 'idle' });
  let selected = $state('');

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  const loadCandidates = async (): Promise<void> => {
    candidates = { kind: 'loading' };

    const result = await listArticleTags(apiKey);
    candidates =
      result.kind === 'ok'
        ? { kind: 'ready', items: result.value }
        : { kind: 'unavailable', message: failureTextOf(result) };
  };

  /* 対象が無いうちは引かない（付ける先が無いため、候補を出しても押せない） */
  const loadCandidatesIfTargeted = (): Promise<void> =>
    articleId === null ? Promise.resolve() : loadCandidates();

  void loadCandidatesIfTargeted();

  /**
   * 結果ごとの後始末。
   *
   * 鍵が断られたのはこの区画だけの問題ではないため、画面全体へ渡す。それ以外はこの区画に留め、同じ
   * 操作をやり直せるようにする。
   */
  const AFTER = {
    ok: (next: readonly AdminArticleTag[]): void => {
      selected = '';
      onChanged(next);
    },
    unauthorized: (_next: readonly AdminArticleTag[], message: string): void => {
      onUnauthorized(message);
    },
    failed: (): void => undefined,
  } satisfies Record<
    ApiResult<unknown>['kind'],
    (next: readonly AdminArticleTag[], message: string) => void
  >;

  /** 送った結果を反映する。成功なら次の一覧、断られたなら理由を残す */
  const apply = (result: ApiResult<unknown>, next: readonly AdminArticleTag[]): void => {
    const message = result.kind === 'ok' ? '' : failureTextOf(result);

    operation = result.kind === 'ok' ? { kind: 'idle' } : { kind: 'rejected', message };
    AFTER[result.kind](next, message);
  };

  const addWith = async (id: string, name: string): Promise<void> => {
    operation = { kind: 'adding' };

    const result = await addArticleTag(apiKey, id, name);
    apply(
      result,
      result.kind === 'ok'
        ? [...tags, { tagId: result.value.tagId, name: result.value.name }]
        : tags,
    );
  };

  const add = (): void => {
    const id = articleId;
    void (id === null ? Promise.resolve() : addWith(id, selected));
  };

  const removeWith = async (id: string, tagId: string): Promise<void> => {
    operation = { kind: 'removing' };

    const result = await removeArticleTag(apiKey, id, tagId);
    apply(result, result.kind === 'ok' ? tags.filter((tag) => tag.tagId !== tagId) : tags);
  };

  const remove = (tagId: string): void => {
    const id = articleId;
    void (id === null ? Promise.resolve() : removeWith(id, tagId));
  };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。
   */
  const candidateItems = $derived(candidates.kind === 'ready' ? candidates.items : []);
  const candidatesMessage = $derived(candidates.kind === 'unavailable' ? candidates.message : null);
  const rejectedMessage = $derived(operation.kind === 'rejected' ? operation.message : null);

  /** 送信中は次の操作へ入らない */
  const busy = $derived(['adding', 'removing'].some((kind) => kind === operation.kind));

  /** まだ付いていない候補だけを選ばせる。付いているものを選べても、同じ結果にしかならない */
  const selectable = $derived(
    candidateItems.filter((candidate) => tags.every((tag) => tag.tagId !== candidate.tagId)),
  );

  const addDisabled = $derived([busy, selected === '', articleId === null].some(Boolean));
</script>

<section class="space-y-2">
  <h2 class="text-base font-medium">タグ</h2>
  <p class="text-muted-foreground text-sm">
    付け外しは押した時点で反映されます（本文の保存とは別の操作です）。
  </p>

  {#if articleId === null}
    <p class="text-muted-foreground text-sm">記事を作成すると、タグを付けられます。</p>
  {:else}
    {#if tags.length === 0}
      <p class="text-muted-foreground text-sm">タグは付いていません。</p>
    {:else}
      <ul class="flex flex-wrap items-center gap-2" data-tags="attached">
        {#each tags as tag (tag.tagId)}
          <li class="flex items-center gap-1">
            <Badge variant="secondary">{tag.name}</Badge>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={busy}
              onclick={() => {
                remove(tag.tagId);
              }}
            >
              外す
            </Button>
          </li>
        {/each}
      </ul>
    {/if}

    {#if candidatesMessage !== null}
      <div class="space-y-2">
        <p class="text-destructive text-sm" role="alert">{candidatesMessage}</p>
        <Button type="button" size="sm" variant="outline" onclick={() => void loadCandidates()}>
          候補を読み直す
        </Button>
      </div>
    {:else if selectable.length === 0}
      <p class="text-muted-foreground text-sm">付けられるタグがありません。</p>
    {:else}
      <div class="flex items-center gap-2">
        <label class="sr-only" for="article-tag-candidate">付けるタグ</label>
        <select
          id="article-tag-candidate"
          class="border-input bg-background rounded-md border px-3 py-2"
          bind:value={selected}
          disabled={busy}
        >
          <option value="">選んでください</option>
          {#each selectable as candidate (candidate.tagId)}
            <option value={candidate.name}>{candidate.name}</option>
          {/each}
        </select>
        <Button type="button" size="sm" disabled={addDisabled} onclick={add}>付ける</Button>
      </div>
    {/if}

    {#if rejectedMessage !== null}
      <p class="text-destructive text-sm" role="alert">{rejectedMessage}</p>
    {/if}
  {/if}
</section>
