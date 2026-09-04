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
    /**
     * 影響を受けるものの一覧。
     *
     * null は**まだ分かっていない**ことを表し、そのときは `pendingNotice` を出す。空配列は「取得できて
     * 影響が無い」ことを表す。この2つを同じ形で受けると、取得できていない状態を「影響なし」と描いて
     * しまう（実際にそうなっていた）。
     */
    readonly affected: readonly { readonly key: string; readonly label: string }[] | null;
    /** 影響一覧の見出し。何の一覧かは呼び出し側の操作で変わる */
    readonly affectedHeading: string;
    /** 影響が分かっていないときに一覧の代わりに出す文言 */
    readonly pendingNotice: string;
    readonly confirmLabel: string;
    /** 確定を塞ぐか。影響範囲を取得できていない間は塞ぐ */
    readonly confirmDisabled: boolean;
    /** 取り消しを塞ぐか。実行中だけ塞ぐ（結果を受け取る前に閉じさせない） */
    readonly cancelDisabled: boolean;
    /** 実行中。確定の文言が変わる */
    readonly running: boolean;
    /** 失敗の文言。null なら出さない */
    readonly failureMessage: string | null;
    /** やり直す操作。取得できていない前提を問い直す場合だけ渡す */
    readonly onRetry: (() => void) | null;
    readonly onConfirm: () => void;
    readonly onCancel: () => void;
  };

  const {
    open,
    title,
    description,
    affected,
    affectedHeading,
    pendingNotice,
    confirmLabel,
    confirmDisabled,
    cancelDisabled,
    running,
    failureMessage,
    onRetry,
    onConfirm,
    onCancel,
  }: Props = $props();

  /*
   * CLOSE-IS-CANCEL: 覆いのクリックや Esc でも閉じられる。閉じる要求は取り消しとして扱い、開く要求は
   * 無視する（開くかどうかは呼び出し側が `open` で決めており、ここは従うだけ）。閉じてよいかの判断も
   * 呼び出し側が持つ。
   */
  const OPEN_CHANGE: readonly (() => void)[] = [
    /* 閉じる要求（`open` が false になる） */
    (): void => {
      onCancel();
    },
    /* 開く要求。ここでは何もしない */
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

      {#if affected === null}
        <p class="text-muted-foreground text-sm">{pendingNotice}</p>
      {:else if affected.length === 0}
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
      <Button variant="outline" disabled={cancelDisabled} onclick={onCancel}>やめる</Button>

      {#if onRetry !== null}
        <Button variant="outline" onclick={onRetry}>もう一度確認する</Button>
      {/if}

      <Button variant="destructive" disabled={confirmDisabled} onclick={onConfirm}>
        {running ? '実行しています…' : confirmLabel}
      </Button>
    </Dialog.Footer>
  </Dialog.Content>
</Dialog.Root>
