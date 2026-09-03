<script lang="ts">
  import { listAlbums, type AdminAlbum, type ApiResult } from '$lib/api/client';
  import { forgetApiKey, rememberApiKey, storedApiKey } from '$lib/credentials';
  import { formatCalendarDate } from '$lib/format';

  /**
   * 画面の状態。
   *
   * 鍵待ち・読み込み中・一覧・失敗を1つの型で表す。個別の真偽値を並べると、読み込み中でありながら
   * 一覧も出ている、といった表せてはいけない組み合わせが作れてしまう。
   */
  type View =
    | { readonly kind: 'locked'; readonly message: string | null }
    | { readonly kind: 'loading' }
    | { readonly kind: 'ready'; readonly albums: readonly AdminAlbum[] }
    | { readonly kind: 'failed'; readonly message: string };

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

  const toView = (result: ApiResult<readonly AdminAlbum[]>): View =>
    result.kind === 'ok'
      ? { kind: 'ready', albums: result.value }
      : result.kind === 'unauthorized'
        ? { kind: 'locked', message: '鍵が受け付けられませんでした。' }
        : { kind: 'failed', message: result.message };

  const load = async (apiKey: string): Promise<void> => {
    view = { kind: 'loading' };

    const result = await listAlbums(apiKey);
    KEY_STORE[result.kind](apiKey);
    view = toView(result);
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

  const lock = (): void => {
    forgetApiKey();
    view = { kind: 'locked', message: null };
    apiKeyDraft = '';
  };

  /*
   * NARROWING-IN-TEMPLATE: テンプレートの分岐は型の絞り込みを持ち越せないため、状態から取り出した
   * 値をここで用意する。テンプレート側で `view.albums` と書くと、型情報を使う検査が解決できない。
   */
  const albums = $derived(view.kind === 'ready' ? view.albums : []);
  const lockMessage = $derived(view.kind === 'locked' ? view.message : null);
  const failureMessage = $derived(view.kind === 'failed' ? view.message : null);

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

    <button
      class="bg-primary text-primary-foreground rounded-md px-4 py-2 text-sm font-medium"
      type="submit">開く</button
    >
  </form>
{:else if view.kind === 'loading'}
  <p class="text-muted-foreground">読み込んでいます。</p>
{:else if failureMessage !== null}
  <p class="text-destructive" role="alert">{failureMessage}</p>
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
      <table class="w-full text-left text-sm">
        <thead class="text-muted-foreground">
          <tr class="border-border border-b">
            <th class="py-2 font-medium">タイトル</th>
            <th class="py-2 font-medium">アーティスト</th>
            <th class="py-2 font-medium">カタログナンバー</th>
            <th class="py-2 font-medium">リリース日</th>
            <th class="py-2 font-medium">状態</th>
          </tr>
        </thead>
        <tbody>
          {#each albums as album (album.albumId)}
            <tr class="border-border border-b">
              <td class="py-2">{album.title}</td>
              <td class="py-2">{album.artistDisplayName}</td>
              <td class="py-2">{album.catalogNumber ?? '―'}</td>
              <td class="py-2">{formatCalendarDate(album.releaseDate)}</td>
              <td class="py-2">{stateLabel(album)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </div>
{/if}
