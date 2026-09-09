<script lang="ts">
  import { Badge } from '$components/ui/badge/index.js';
  import { Button } from '$components/ui/button/index.js';
  import {
    getAlbum,
    removeArticleAlbum,
    searchAlbums,
    setArticleAlbum,
    type AlbumCandidate,
    type ApiResult,
  } from '$lib/api/client';
  import { KEY_STORE } from '$lib/credentials';

  /**
   * 記事が参照する作品。
   *
   * <p>
   * タグと同じく**本文の保存とは別の経路**で、押した時点で反映される。参照を持てるのは `ALBUM` 種別
   * だけで（DECISIONS 21）、この区画を出すかどうかは画面全体が種別から決める。
   * </p>
   *
   * <p>
   * 未存在・非公開などの判定はバックエンドが返す。画面は候補を並べ、返った結果を扱うだけで、参照できる
   * かどうかを先に判定しない。
   * </p>
   */
  type Props = {
    readonly apiKey: string;
    /** 対象の記事。まだ作られていなければ null */
    readonly articleId: string | null;
    /** いま参照している作品。無ければ null */
    readonly albumId: string | null;
    /** 鍵が断られたときに画面全体へ渡す。鍵待ちへ戻すかはこの区画では決めない */
    readonly onUnauthorized: (message: string) => void;
    /** 参照の変更を画面全体へ渡す。外したときは null */
    readonly onChanged: (albumId: string | null) => void;
  };

  const { apiKey, articleId, albumId, onUnauthorized, onChanged }: Props = $props();

  /**
   * いま参照している作品の見えかた。
   *
   * 参照が無いことと、参照はあるが読めていないことを混ぜない。後者で「参照なし」と描くと、外していない
   * ものを外したように見せる。
   */
  type Reference =
    | { readonly kind: 'none' }
    | { readonly kind: 'loading' }
    | { readonly kind: 'ready'; readonly title: string; readonly catalogNumber: string | null }
    | { readonly kind: 'unavailable'; readonly message: string };

  /** 探した結果。探していない状態と、探して0件だった状態を混ぜない */
  type Search =
    | { readonly kind: 'idle' }
    | { readonly kind: 'searching' }
    | { readonly kind: 'found'; readonly items: readonly AlbumCandidate[] }
    | { readonly kind: 'unavailable'; readonly message: string };

  /** 進行中のこと。1つずつしか受け付けない */
  type Operation =
    | { readonly kind: 'idle' }
    | { readonly kind: 'linking' }
    | { readonly kind: 'unlinking' }
    | { readonly kind: 'rejected'; readonly message: string };

  /* 参照があれば、下の読み込みが最初の描画より先に `loading` へ移す */
  let reference = $state<Reference>({ kind: 'none' });
  let search = $state<Search>({ kind: 'idle' });
  let operation = $state<Operation>({ kind: 'idle' });
  let keyword = $state('');
  let by = $state('title');

  /** 失敗した結果 */
  type ApiFailure = Exclude<ApiResult<unknown>, { readonly kind: 'ok' }>;

  const failureTextOf = (failure: ApiFailure): string =>
    failure.kind === 'unauthorized' ? '鍵が受け付けられませんでした。' : failure.message;

  /**
   * 鍵が断られたかどうかで、画面全体へ渡すかを分ける。
   *
   * 鍵が断られたのはこの区画だけの問題ではない。ここへ「やり直す」だけを出して留めると、鍵の入力へ
   * 戻れないまま何度やり直しても断られる。
   */
  const ESCALATE = {
    ok: (): void => undefined,
    unauthorized: (message: string): void => {
      onUnauthorized(message);
    },
    failed: (): void => undefined,
  } satisfies Record<ApiResult<unknown>['kind'], (message: string) => void>;

  /** 参照している作品を引く。名前を出さないと、どれを参照しているのか読めない */
  const loadReference = async (id: string): Promise<void> => {
    reference = { kind: 'loading' };

    const result = await getAlbum(apiKey, id);
    const message = result.kind === 'ok' ? '' : failureTextOf(result);

    KEY_STORE[result.kind](apiKey);
    reference =
      result.kind === 'ok'
        ? { kind: 'ready', title: result.value.title, catalogNumber: result.value.catalogNumber }
        : { kind: 'unavailable', message };
    ESCALATE[result.kind](message);
  };

  const loadReferenceIfLinked = (): Promise<void> =>
    albumId === null ? Promise.resolve() : loadReference(albumId);

  void loadReferenceIfLinked();

  const searchWith = async (target: 'title' | 'catalogNumber', word: string): Promise<void> => {
    search = { kind: 'searching' };

    const result = await searchAlbums(apiKey, target, word);
    const message = result.kind === 'ok' ? '' : failureTextOf(result);

    KEY_STORE[result.kind](apiKey);
    search =
      result.kind === 'ok'
        ? { kind: 'found', items: result.value }
        : { kind: 'unavailable', message };
    ESCALATE[result.kind](message);
  };

  /** 参照を差し替えた結果を反映する。断られたなら理由を残し、参照は動かさない */
  const applyLink = (
    result: ApiResult<unknown>,
    next: Reference,
    nextAlbumId: string | null,
  ): void => {
    const message = result.kind === 'ok' ? '' : failureTextOf(result);

    KEY_STORE[result.kind](apiKey);
    operation = result.kind === 'ok' ? { kind: 'idle' } : { kind: 'rejected', message };
    reference = result.kind === 'ok' ? next : reference;
    search = result.kind === 'ok' ? { kind: 'idle' } : search;
    ESCALATE[result.kind](message);

    LINKED[result.kind](nextAlbumId);
  };

  const LINKED = {
    ok: (nextAlbumId: string | null): void => {
      onChanged(nextAlbumId);
    },
    unauthorized: (): void => undefined,
    failed: (): void => undefined,
  } satisfies Record<ApiResult<unknown>['kind'], (nextAlbumId: string | null) => void>;

  const linkWith = async (id: string, candidate: AlbumCandidate): Promise<void> => {
    operation = { kind: 'linking' };

    const result = await setArticleAlbum(apiKey, id, candidate.albumId);
    applyLink(
      result,
      { kind: 'ready', title: candidate.title, catalogNumber: candidate.catalogNumber },
      candidate.albumId,
    );
  };

  const unlinkWith = async (id: string): Promise<void> => {
    operation = { kind: 'unlinking' };

    const result = await removeArticleAlbum(apiKey, id);
    applyLink(result, { kind: 'none' }, null);
  };

  const link = (candidate: AlbumCandidate): void => {
    const id = articleId;
    void (id === null ? Promise.resolve() : linkWith(id, candidate));
  };

  const unlink = (): void => {
    const id = articleId;
    void (id === null ? Promise.resolve() : unlinkWith(id));
  };

  const submitSearch = (event: SubmitEvent): void => {
    event.preventDefault();
    void searchWith(by === 'catalogNumber' ? 'catalogNumber' : 'title', keyword);
  };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。
   */
  const referenceTitle = $derived(reference.kind === 'ready' ? reference.title : null);
  const referenceCatalogNumber = $derived(
    reference.kind === 'ready' ? reference.catalogNumber : null,
  );
  const referenceLoading = $derived(reference.kind === 'loading');
  const referenceMessage = $derived(reference.kind === 'unavailable' ? reference.message : null);

  const searchItems = $derived(search.kind === 'found' ? search.items : []);
  const searching = $derived(search.kind === 'searching');
  const searchMessage = $derived(search.kind === 'unavailable' ? search.message : null);

  /** 探して0件だった状態。探していない状態と混ぜない */
  const searchedEmpty = $derived(search.kind === 'found' && search.items.length === 0);
  const rejectedMessage = $derived(operation.kind === 'rejected' ? operation.message : null);

  /** 送信中は次の操作へ入らない */
  const busy = $derived(['linking', 'unlinking'].some((kind) => kind === operation.kind));
  const searchDisabled = $derived([busy, searching, keyword === ''].some(Boolean));
</script>

<section class="space-y-2">
  <h2 class="text-base font-medium">参照する作品</h2>
  <p class="text-muted-foreground text-sm">
    付け外しは押した時点で反映されます（本文の保存とは別の操作です）。
  </p>

  {#if articleId === null}
    <p class="text-muted-foreground text-sm">記事を作成すると、作品を参照できます。</p>
  {:else}
    {#if referenceLoading}
      <p class="text-muted-foreground text-sm">参照している作品を読み込んでいます。</p>
    {:else if referenceMessage !== null}
      <p class="text-destructive text-sm" role="alert">{referenceMessage}</p>
    {:else if referenceTitle === null}
      <p class="text-muted-foreground text-sm">作品を参照していません。</p>
    {:else}
      <div class="flex items-center gap-2" data-album="linked">
        <Badge variant="secondary">{referenceTitle}</Badge>
        {#if referenceCatalogNumber !== null}
          <span class="text-muted-foreground text-sm">{referenceCatalogNumber}</span>
        {/if}
        <Button type="button" size="sm" variant="outline" disabled={busy} onclick={unlink}>
          参照を外す
        </Button>
      </div>
    {/if}

    <!--
      この区画は本体の保存のフォームの外に置かれる（画面全体がそう組んでいる）。したがってここで
      フォームを持てる。Enter で探せるのは、本体の保存を巻き込まないためでもある。
    -->
    <form class="flex flex-wrap items-center gap-2" onsubmit={submitSearch}>
      <label class="sr-only" for="album-search-by">探す項目</label>
      <select
        id="album-search-by"
        class="border-input bg-background rounded-md border px-3 py-2"
        bind:value={by}
        disabled={busy}
      >
        <option value="title">タイトル</option>
        <option value="catalogNumber">カタログナンバー</option>
      </select>

      <label class="sr-only" for="album-search-keyword">探す語</label>
      <input
        id="album-search-keyword"
        class="border-input bg-background rounded-md border px-3 py-2"
        type="text"
        bind:value={keyword}
        disabled={busy}
      />

      <Button type="submit" size="sm" variant="outline" disabled={searchDisabled}>探す</Button>
    </form>

    {#if searching}
      <p class="text-muted-foreground text-sm">探しています。</p>
    {:else if searchMessage !== null}
      <p class="text-destructive text-sm" role="alert">{searchMessage}</p>
    {:else if searchedEmpty}
      <p class="text-muted-foreground text-sm">見つかりませんでした。</p>
    {:else if searchItems.length > 0}
      <ul class="space-y-1" data-album="candidates">
        {#each searchItems as candidate (candidate.albumId)}
          <li class="flex items-center gap-2">
            <span class="text-sm">{candidate.title}</span>
            <span class="text-muted-foreground text-sm">{candidate.catalogNumber ?? '―'}</span>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={busy}
              onclick={() => {
                link(candidate);
              }}
            >
              参照する
            </Button>
          </li>
        {/each}
      </ul>
    {/if}

    {#if rejectedMessage !== null}
      <p class="text-destructive text-sm" role="alert">{rejectedMessage}</p>
    {/if}
  {/if}
</section>
