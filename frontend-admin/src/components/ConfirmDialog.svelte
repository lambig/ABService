<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import * as Dialog from '$components/ui/dialog/index.js';
  import type { Snippet } from 'svelte';

  /**
   * 実行の前に確認を求める対話。
   *
   * <p>
   * 持つのは対話の骨格（開閉・確定・取り消し・実行中・失敗）だけで、確認のために何を見せるかは
   * 呼び出し側が渡す。**何を見せる必要があるかは操作で変わる**——作品の削除は影響範囲の一覧を伴い
   * （#274）、記事の削除は伴わない（記事を指す集約が無く、照会する前提が無い。DECISIONS 27）。
   * </p>
   */
  type Props = {
    readonly open: boolean;
    readonly title: string;
    readonly description: string;
    readonly confirmLabel: string;
    /** 確定を塞ぐか。確認に要るものが揃っていない間は塞ぐ */
    readonly confirmDisabled: boolean;
    /** 取り消しを塞ぐか。実行中だけ塞ぐ（結果を受け取る前に閉じさせない） */
    readonly cancelDisabled: boolean;
    /** 実行中。確定の文言が変わる */
    readonly running: boolean;
    /** 失敗の文言。null なら出さない */
    readonly failureMessage: string | null;
    /** やり直す操作。確定の前に取り直せるものがある場合だけ渡す */
    readonly onRetry: (() => void) | null;
    readonly onConfirm: () => void;
    readonly onCancel: () => void;
    /** 確認のために見せるもの。見せるものが無い操作では渡さない */
    readonly children?: Snippet;
  };

  const {
    open,
    title,
    description,
    confirmLabel,
    confirmDisabled,
    cancelDisabled,
    running,
    failureMessage,
    onRetry,
    onConfirm,
    onCancel,
    children,
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

    {@render children?.()}

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
