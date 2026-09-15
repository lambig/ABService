<script lang="ts" generics="T extends object">
  import { onDestroy, untrack } from 'svelte';
  import { Button } from '$components/ui/button/index.js';
  import {
    createEditorRecovery,
    type RecoveryIdentity,
    type RecoverySnapshot,
  } from '$lib/editor-recovery';
  const {
    identity,
    values,
    revision,
    decode,
    onRestore,
    onPending,
    continued = false,
  }: {
    readonly continued?: boolean;
    readonly identity: RecoveryIdentity;
    readonly values: T;
    readonly revision: number | null;
    readonly decode: (value: unknown) => T | null;
    readonly onRestore: (snapshot: RecoverySnapshot<T>) => void;
    readonly onPending: (pending: boolean) => void;
  } = $props();
  const recovery = untrack(() =>
    createEditorRecovery(identity, { values, revision }, decode, () => sessionStorage, continued),
  );
  $effect(() => {
    recovery.capture({ values, revision });
  });
  $effect(() => {
    onPending($recovery.awaitingChoice);
  });
  onDestroy(() => {
    recovery.dispose();
  });
  /** 保存成功・最新の読み直しは保留中の書き込みも取り消す。 */
  export const clear = (): void => {
    recovery.capture({ values, revision });
    recovery.clear();
  };
  const restore = (): void => {
    const snapshot = recovery.restore();
    const apply =
      snapshot === null
        ? null
        : () => {
            onRestore(snapshot);
          };
    apply?.();
  };
</script>

<svelte:window
  onpagehide={() => {
    recovery.flush();
  }}
/>
<section class="border-border space-y-2 rounded-md border p-4 text-sm" aria-label="入力の復旧">
  {#if $recovery.pending !== null}
    <p>
      この入力の退避データがあります（{new Date($recovery.pending.savedAt).toLocaleString()}）。
    </p>
    <p>復元するか破棄するかを選ぶと、編集を続けられます。</p>
    <div class="flex flex-wrap gap-3">
      <Button type="button" onclick={restore}>退避した入力を復元する</Button>
      <Button type="button" variant="outline" onclick={clear}>退避データを破棄する</Button>
    </div>
  {:else}
    <p>
      {$recovery.savedAt === null
        ? '入力はこのタブに退避します。'
        : '入力をこのタブに退避しました。'} タブを閉じると失われます。
    </p>
  {/if}
  {#if $recovery.error !== null}
    <p role="alert">{$recovery.error}</p>
    <Button type="button" variant="outline" onclick={clear}>退避データを破棄する</Button>
  {/if}
</section>
