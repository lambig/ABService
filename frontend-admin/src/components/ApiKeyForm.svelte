<script lang="ts">
  import { Button } from '$components/ui/button/index.js';

  /**
   * 管理APIの鍵を受け取る入り口。
   *
   * 鍵の保持の方針（このタブの間だけ・成果物へ焼き込まない）は `$lib/credentials` が持つ。ここは
   * 受け取って渡すところだけを担う。
   */
  type Props = {
    /** 断られた理由。null なら出さない */
    readonly message: string | null;
    readonly onSubmit: (apiKey: string) => void;
  };

  const { message, onSubmit }: Props = $props();

  let draft = $state('');

  const submit = (event: SubmitEvent): void => {
    event.preventDefault();
    onSubmit(draft);
  };
</script>

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
    bind:value={draft}
  />

  {#if message !== null}
    <p class="text-destructive text-sm" role="alert">{message}</p>
  {/if}

  <Button type="submit">開く</Button>
</form>
