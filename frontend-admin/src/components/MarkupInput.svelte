<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import { MARKDOWN_ASSISTANCE, type MarkdownAssistance } from '$lib/markdown-assistance';
  import { tick } from 'svelte';

  type Props = {
    readonly id: string;
    readonly value: string;
    readonly markdown: boolean;
    readonly disabled: boolean;
    readonly invalid: boolean;
    readonly onEdit: (value: string) => void;
  };

  const { id, value, markdown, disabled, invalid, onEdit }: Props = $props();
  let input = $state<HTMLTextAreaElement | null>(null);

  const apply = async (source: MarkdownAssistance, element: HTMLTextAreaElement): Promise<void> => {
    const suggestion = source.suggest({
      value: element.value,
      start: element.selectionStart,
      end: element.selectionEnd,
    });
    onEdit(suggestion.value);
    await tick();
    /* LATE-FOCUS: 保存・画面破棄や別入力の後へ古い選択範囲を適用しない。 */
    const editable =
      element.isConnected && element.value === suggestion.value && element.matches(':enabled')
        ? element
        : null;
    editable?.focus();
    editable?.setSelectionRange(suggestion.start, suggestion.end);
  };
</script>

{#if markdown}
  <div class="flex flex-wrap gap-2" role="group" aria-label="Markdown入力支援">
    {#each MARKDOWN_ASSISTANCE as source (source.id)}
      <Button
        type="button"
        size="sm"
        variant="outline"
        {disabled}
        aria-controls={id}
        onclick={() => {
          void (input === null ? undefined : apply(source, input));
        }}>{source.label}</Button
      >
    {/each}
  </div>
  <p class="text-muted-foreground text-sm">
    選択した文字に記法を付けます。未選択なら入力位置に挿入します。
  </p>
{/if}
<textarea
  bind:this={input}
  {id}
  class="border-input bg-background w-full rounded-md border px-3 py-2"
  rows="6"
  {value}
  {disabled}
  aria-invalid={invalid}
  oninput={(event) => {
    onEdit(event.currentTarget.value);
  }}></textarea>
