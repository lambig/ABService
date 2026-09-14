<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import type { ExternalAudioDraft } from '$lib/api/album-form';

  /**
   * 作品が持つ外部音源。
   *
   * <p>
   * 追加・取り外し・並べ替えはどれも<b>入力の操作</b>で、作品の保存に乗って初めて反映される（#391）。押した
   * 時点で送る経路は持たないため、保存するまでは取り消せる。
   * </p>
   *
   * <p>
   * <b>並びは配列の位置がそのまま表す。</b> 行に出す番号も位置から描く——未保存の並べ替えや取り外しがある間、
   * 読み込んだ時点の表示順は画面の並びと食い違う。
   * </p>
   *
   * <p>
   * 埋め込めるホストかどうかの判定はバックエンドが持つ。画面は入れられたURLをそのまま保存へ乗せ、断られた
   * 理由をその行に出すだけで、送る前に弾かない。
   * </p>
   */
  type Props = {
    /** いま入力している外部音源。この並びがそのまま作品の音源になる */
    readonly audios: readonly ExternalAudioDraft[];
    /** 入力を触らせない間（保存中・画像の送信中） */
    readonly disabled: boolean;
    /** その行に割り当てられた誤り。位置は `externalAudios[i].url` で返る */
    readonly messagesOf: (index: number) => readonly string[];
    /** 変えた並びを画面全体へ返す。保存はこの区画では行わない */
    readonly onChange: (audios: readonly ExternalAudioDraft[]) => void;
  };

  const { audios, disabled, messagesOf, onChange }: Props = $props();

  let url = $state('');

  const add = (event: SubmitEvent): void => {
    event.preventDefault();
    onChange([...audios, { externalAudioId: null, url }]);
    url = '';
  };

  const remove = (index: number): void => {
    onChange(audios.filter((_, position) => position !== index));
  };

  /**
   * 入れ替えた並び。
   *
   * 番号を持たないため、配列の要素を入れ替えるだけで済む（詰め直しも振り直しも要らない）。
   */
  const swapped = (index: number, other: number): readonly ExternalAudioDraft[] =>
    audios.map((audio, position) =>
      position === index
        ? (audios[other] ?? audio)
        : position === other
          ? (audios[index] ?? audio)
          : audio,
    );

  const move = (index: number, other: number): void => {
    onChange(swapped(index, other));
  };

  const addDisabled = $derived([disabled, url === ''].some(Boolean));
  const last = $derived(audios.length - 1);
</script>

<!-- 見出しは区画（`AlbumSection`）が持つ。畳んだときの要約と同じ場所に出すため -->
<div class="space-y-4">
  <p class="text-muted-foreground text-sm">
    追加・取り外し・並べ替えは、作品を保存したときに反映されます。
  </p>

  {#if audios.length === 0}
    <p class="text-muted-foreground text-sm">外部音源はありません。</p>
  {:else}
    <ul class="space-y-1" data-external-audios>
      <!--
        ROW-KEYED-BY-POSITION: 足したばかりの行はIDを持たず、URLは入力の途中で重複しうる。並びの中で
        一意に決まるのは位置だけである。
      -->
      {#each audios as audio, index (index)}
        <li class="space-y-1">
          <div class="flex items-center gap-2">
            <span class="text-muted-foreground w-6 text-sm">{index + 1}</span>
            <span class="min-w-0 flex-1 truncate text-sm">{audio.url}</span>

            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={[disabled, index === 0].some(Boolean)}
              onclick={() => {
                move(index, index - 1);
              }}
            >
              上へ
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={[disabled, index === last].some(Boolean)}
              onclick={() => {
                move(index, index + 1);
              }}
            >
              下へ
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              {disabled}
              onclick={() => {
                remove(index);
              }}
            >
              外す
            </Button>
          </div>

          {#each messagesOf(index) as message (message)}
            <p class="text-destructive pl-8 text-sm" role="alert">{message}</p>
          {/each}
        </li>
      {/each}
    </ul>
  {/if}

  <!--
    ADD-IS-NOT-SUBMIT: この区画は作品の保存のフォームの外に置かれる（画面全体がそう組んでいる）。
    したがってここでフォームを持てる。Enter で行を足せるのは、作品の保存を巻き込まないためでもある。
  -->
  <form class="flex flex-wrap items-end gap-2" onsubmit={add}>
    <div class="min-w-0 flex-1 space-y-1">
      <label class="text-sm font-medium" for="album-external-audio-url">音源のURL</label>
      <input
        id="album-external-audio-url"
        class="border-input bg-background w-full rounded-md border px-3 py-2"
        type="text"
        bind:value={url}
        {disabled}
      />
    </div>
    <Button type="submit" size="sm" variant="outline" disabled={addDisabled}>音源を追加する</Button>
  </form>
</div>
