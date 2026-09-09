<script lang="ts">
  import ConfirmDialog from '$components/ConfirmDialog.svelte';

  /**
   * 影響範囲を伴う操作の事前確認。
   *
   * 影響範囲は**バックエンドの照会が返したものをそのまま並べる**（#274）。ここで「どれが非公開に
   * なるか」を組み立て直さないため、このコンポーネントは判定を持たず、渡された行を描くだけ。対話の
   * 骨格は {@link ConfirmDialog} が持ち、ここは影響一覧の見せかたに限る。
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
</script>

<ConfirmDialog
  {open}
  {title}
  {description}
  {confirmLabel}
  {confirmDisabled}
  {cancelDisabled}
  {running}
  {failureMessage}
  {onRetry}
  {onConfirm}
  {onCancel}
>
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
</ConfirmDialog>
