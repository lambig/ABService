<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
  import type { Snippet } from 'svelte';

  /**
   * 作品の編集の区画1つ。
   *
   * <p>
   * <b>既定は畳んだ形。</b> 作品1件の入力は縦に長く、全部を開いたままにすると、いま何を編集しているのかを
   * 見失う。畳んだ区画は公開サイトと同じ読み方の要約で並び、開いた区画だけが入力に変わる——畳み切った
   * 画面は、その作品の姿そのものになる。
   * </p>
   *
   * <p>
   * 開閉は見出しの行の先頭の印（`>`）で表す。文言のボタンを区画ごとに置くと、畳んだ区画が並んだときに
   * <b>その区画が何なのかより「開く」の方が目に入る</b>。何の区画なのかは `aria-label` が、開いているか
   * どうかは `aria-expanded` が持つ——印だけでは、読み上げでどちらも読めない。
   * </p>
   *
   * <p>
   * <b>断られた区画は畳めない。</b> 理由は欄の下に出るため、畳んだままでは直す先が画面から消える。
   * </p>
   */
  type Props = {
    readonly heading: string;
    /** 畳んだときに出す要約。入っているものが無ければ、無いことを示す文言 */
    readonly summary: string;
    readonly open: boolean;
    /** 入力を触らせない間（保存中） */
    readonly disabled: boolean;
    readonly onToggle: () => void;
    readonly children: Snippet;
  };

  const { heading, summary, open, disabled, onToggle, children }: Props = $props();

  /*
   * NARROWING-BY-VALUE: 畳んでいることを表す値を持つ。テンプレートで否定を書くより、どちらの姿を
   * 描くのかが読める。
   */
  const collapsed = $derived(open ? false : true);
</script>

<section class="max-w-2xl space-y-4" data-section={heading}>
  <div class="flex items-center gap-2">
    <Button
      type="button"
      size="icon-sm"
      variant="ghost"
      {disabled}
      aria-expanded={open}
      aria-label={open ? `${heading}を畳む` : `${heading}を開く`}
      onclick={onToggle}
    >
      <!-- 印の向きが開閉を表す。開いた区画では右向きが下を向く -->
      <ChevronRightIcon class="transition-transform {open ? 'rotate-90' : ''}" />
    </Button>

    <h2 class="shrink-0 text-base font-medium">{heading}</h2>

    {#if collapsed}
      <span class="text-muted-foreground min-w-0 flex-1 truncate text-sm" data-section-summary>
        {summary}
      </span>
    {/if}
  </div>

  {#if open}
    {@render children()}
  {/if}
</section>
