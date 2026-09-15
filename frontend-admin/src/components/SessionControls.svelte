<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import { logout, sessionState } from '$lib/credentials';
  const { onLogout }: { readonly onLogout: () => void } = $props();
  let notice = $state<{ readonly generation: symbol; readonly message: string | null } | null>(
    null,
  );
  const leave = async (): Promise<void> => {
    const { completion } = logout();
    const generation = $sessionState.generation;
    onLogout();
    notice = { generation, message: 'このタブの認証情報を破棄しました。' };
    const message = await completion;
    notice = $sessionState.generation === generation ? { generation, message } : notice;
  };
</script>

<div class="mb-4 space-y-2">
  {#if $sessionState.session !== null}
    <div class="flex items-center justify-end gap-3">
      <span class="text-muted-foreground text-sm"
        >有効期限: {new Date($sessionState.session.expiresAt).toLocaleTimeString()}</span
      >
      <Button variant="outline" onclick={() => void leave()}>ログアウト</Button>
    </div>
  {:else if $sessionState.authenticating}
    <Button variant="outline" onclick={() => void leave()}>認証を取り消す</Button>
  {:else if notice !== null && notice.generation === $sessionState.generation && notice.message !== null}
    <p role="status" class="text-sm">{notice.message}</p>
  {/if}
</div>
