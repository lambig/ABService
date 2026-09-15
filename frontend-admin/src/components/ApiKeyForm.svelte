<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import { authenticate, sessionState, type AdminSession } from '$lib/credentials';

  /**
   * 管理APIの鍵を受け取る入り口。
   *
   * 入力キーを交換にだけ使い、受け付けられたセッションを画面へ渡す。
   */
  type Props = {
    /** 断られた理由。null なら出さない */
    readonly message: string | null;
    readonly onSubmit: (session: AdminSession) => void;
  };

  const { message, onSubmit }: Props = $props();

  let draft = $state('');
  let failure = $state<string | null>(null);

  const submit = async (event: SubmitEvent): Promise<void> => {
    event.preventDefault();
    const pending = authenticate(draft);
    draft = '';
    failure = null;
    const result = await pending;
    const accept =
      result?.kind === 'ok'
        ? () => {
            onSubmit(result.value);
          }
        : () => undefined;
    accept();
    failure =
      result === null
        ? failure
        : result.kind === 'ok'
          ? null
          : result.kind === 'unauthorized'
            ? '鍵が受け付けられませんでした。'
            : result.message;
  };
</script>

<form class="max-w-md space-y-4" onsubmit={(event) => void submit(event)}>
  <div class="space-y-1">
    <label class="text-sm font-medium" for="api-key">管理APIの鍵</label>
    <p class="text-muted-foreground text-sm">
      鍵は認証時だけ使用し、保存しません。このタブでは30分有効なトークンを使用します。
    </p>
  </div>

  <input
    id="api-key"
    class="border-input bg-background w-full rounded-md border px-3 py-2"
    type="password"
    autocomplete="off"
    bind:value={draft}
    disabled={$sessionState.authenticating}
  />

  {#if (failure ?? message) !== null}
    <p class="text-destructive text-sm" role="alert">{failure ?? message}</p>
  {/if}

  <Button type="submit" disabled={$sessionState.authenticating}
    >{$sessionState.authenticating ? '認証中…' : '開く'}</Button
  >
</form>
