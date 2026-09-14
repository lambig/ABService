<script lang="ts">
  import { fieldIdOf, type FieldSpec } from '$lib/api/album-form';

  /**
   * 作品の入力欄1つ。
   *
   * <p>
   * 行の中にも、保存のフォームの外（原作の出典は曲目の直後に置く）にも同じ形で現れる。**どの欄も `form`
   * でフォームを名指す**ため、囲みの中にあるかどうかに関わらず同じ保存へ乗る。
   * </p>
   *
   * <p>
   * 検証エラーの文言はここに出す。位置（`field`）と欄は綴りで対応しており、対応表を持たない（#288）。
   * </p>
   */
  type Props = {
    readonly field: FieldSpec;
    readonly value: string;
    /** 結び付けるフォームの名。欄がフォームの外にあっても同じ保存へ乗せる */
    readonly formId: string;
    /** この欄に割り当てられた検証エラー。無ければ空 */
    readonly messages: readonly string[];
    readonly onValue: (value: string) => void;
  };

  const { field, value, formId, messages, onValue }: Props = $props();

  /** 選択肢の表示。値は管理APIの列挙子名で、そのままでは画面に出せない */
  const CHOICE_LABELS: Readonly<Record<string, string>> = {
    PLAIN_TEXT: 'プレーンテキスト',
    MARKDOWN: 'Markdown',
  };

  /**
   * 1行の入力欄が受け取る型。
   *
   * 複数行・選択肢は別の枝が描くため、ここへは来ない。来ない種別も表へ載せるのは、種別が増えたときに
   * 抜けをコンパイルで気付くため。
   */
  const INPUT_TYPES = {
    text: 'text',
    date: 'date',
    number: 'number',
    multiline: 'text',
    choice: 'text',
  } as const satisfies Record<FieldSpec['kind'], string>;

  const id = $derived(fieldIdOf(field.path));
  const invalid = $derived(messages.length > 0);
</script>

<label class="text-sm font-medium" for={id}>{field.label}</label>

{#if field.kind === 'choice'}
  <select
    {id}
    form={formId}
    class="border-input bg-background w-full rounded-md border px-3 py-2"
    {value}
    aria-invalid={invalid}
    onchange={(event) => {
      onValue(event.currentTarget.value);
    }}
  >
    {#each field.choices as choice (choice)}
      <option value={choice}>{CHOICE_LABELS[choice] ?? choice}</option>
    {/each}
  </select>
{:else if field.kind === 'multiline'}
  <textarea
    {id}
    form={formId}
    class="border-input bg-background w-full rounded-md border px-3 py-2"
    rows="4"
    {value}
    aria-invalid={invalid}
    oninput={(event) => {
      onValue(event.currentTarget.value);
    }}></textarea>
{:else}
  <input
    {id}
    form={formId}
    class="border-input bg-background w-full rounded-md border px-3 py-2"
    type={INPUT_TYPES[field.kind]}
    {value}
    aria-invalid={invalid}
    oninput={(event) => {
      onValue(event.currentTarget.value);
    }}
  />
{/if}

{#each messages as message (message)}
  <p class="text-destructive text-sm" role="alert">{message}</p>
{/each}
