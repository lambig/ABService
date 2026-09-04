<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import * as Dialog from '$components/ui/dialog/index.js';

  /**
   * 破壊的な操作の事前確認。
   *
   * 影響範囲は**バックエンドの照会が返したものをそのまま並べる**（#274）。ここで「どれが非公開に
   * なるか」を組み立て直さないため、このコンポーネントは判定を持たず、渡された行を描くだけ。
   */
  type Props = {
    readonly open: boolean;
    readonly title: string;
    readonly description: string;
    /** 影響を受けるものの一覧。空なら「影響なし」の文言を出す */
    readonly affected: readonly { readonly key: string; readonly label: string }[];
    /** 影響一覧の見出し。何の一覧かは呼び出し側の操作で変わる */
    readonly affectedHeading: string;
    readonly confirmLabel: string;
    /** 実行中は確定と取り消しを塞ぐ（二重送信を防ぐ） */
    readonly busy: boolean;
    /** 実行が失敗したときの文言。null なら出さない */
    readonly failureMessage: string | null;
    readonly onConfirm: () => void;
    readonly onCancel: () => void;
  };

  const {
    open,
    title,
    description,
    affected,
    affectedHeading,
    confirmLabel,
    busy,
    failureMessage,
    onConfirm,
    onCancel,
  }: Props = $props();

  /*
   * CLOSE-IS-CANCEL: 覆いのクリックや Esc でも閉じられる。閉じる要求は取り消しとして扱い、開く要求は
   * 無視する（開いているかどうかは呼び出し側が `open` で決めており、ここは従うだけ）。閉じてよいかの
   * 判断も呼び出し側が持つ（実行中は閉じさせない等）。
   */
  const OPEN_CHANGE: readonly (() => void)[] = [
    /* 閉じる要求（`open` が false になる） */
    (): void => {
      onCancel();
    },
    /* 開く要求。開くかどうかは呼び出し側が `open` で決めるため、ここでは何もしない */
    (): void => undefined,
  ];

  const onOpenChange = (nextOpen: boolean): void => {
    OPEN_CHANGE[Number(nextOpen)]?.();
  };
</script>

<Dialog.Root {open} {onOpenChange}>
  <Dialog.Content showCloseButton={false}>
    <Dialog.Header>
      <Dialog.Title>{title}</Dialog.Title>
      <Dialog.Description>{description}</Dialog.Description>
    </Dialog.Header>

    <section class="space-y-2">
      <h3 class="text-sm font-medium">{affectedHeading}</h3>

      {#if affected.length === 0}
        <p class="text-muted-foreground text-sm">影響を受けるものはありません。</p>
      {:else}
        <ul class="text-sm">
          {#each affected as item (item.key)}
            <li class="border-border border-b py-2 last:border-b-0">{item.label}</li>
          {/each}
        </ul>
      {/if}
    </section>

    {#if failureMessage !== null}
      <p class="text-destructive text-sm" role="alert">{failureMessage}</p>
    {/if}

    <Dialog.Footer>
      <Button variant="outline" disabled={busy} onclick={onCancel}>やめる</Button>
      <Button variant="destructive" disabled={busy} onclick={onConfirm}>
        {busy ? '実行しています…' : confirmLabel}
      </Button>
    </Dialog.Footer>
  </Dialog.Content>
</Dialog.Root>
