<script lang="ts">
  import AlbumTrackEditor from '$components/AlbumTrackEditor.svelte';
  import { Button } from '$components/ui/button/index.js';
  import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
  import {
    EMPTY_TRACK,
    TRACK_FIELDS,
    TUNE_FIELDS,
    trackPathOf,
    trackTitleOf,
    tunePathOf,
    tuneSummaryOf,
    type TrackDraft,
  } from '$lib/api/track-form';

  /**
   * 作品の曲目（トラックとチューン構成）。
   *
   * <p>
   * 追加・取り外し・並べ替え・行の編集はどれも<b>入力の操作</b>で、作品の保存に乗って初めて反映される
   * （#391）。押した時点で送る経路は持たないため、保存するまでは取り消せる。
   * </p>
   *
   * <p>
   * <b>並びは配列の位置がそのまま表す。</b> 行に出す番号も位置から描く——未保存の並べ替えや取り外しが
   * ある間、読み込んだ時点のトラック番号は画面の並びと食い違う。
   * </p>
   *
   * <p>
   * 既定は畳んだ形で、出る形（名・名義・チューンの行）で並ぶ。開くとその場が入力に変わる。全部を開いた
   * ままにすると、1件あたりの高さが大きいぶん一覧として読めなくなる。
   * </p>
   *
   * <p>
   * <b>畳んだ形は公開サイトと同じではない。</b> 公開は名もクレジットも持たない行を落とすが（読めないため）、
   * ここでは落とさずに「空の行」として出す。落とすと、入っているものが画面から消えて編集できなくなる。
   * </p>
   *
   * <p>
   * <b>畳んでも書きかけは消えない。</b> 入力は作品の下書きが持っており、この区画が持つのは見え方だけである。
   * </p>
   *
   * <p>
   * チューンは `Tune` マスタと同定しない（#183 の v1.2 まで）。手がかりは曲名の文字列だけで、作曲・編曲の
   * クレジットもこの行が持つ。
   * </p>
   */
  type Props = {
    /** いま入力している曲目。この並びがそのまま作品の曲目になる */
    readonly tracks: readonly TrackDraft[];
    /** 入力を触らせない間（保存中・画像の送信中） */
    readonly disabled: boolean;
    /** 位置に割り当てられた誤り */
    readonly messagesOf: (path: string) => readonly string[];
    /** 変えた並びを画面全体へ返す。保存はこの区画では行わない */
    readonly onChange: (tracks: readonly TrackDraft[]) => void;
  };

  const { tracks, disabled, messagesOf, onChange }: Props = $props();

  /**
   * 開いている行。
   *
   * <b>一度に開くのは1行だけ。</b> 入れ子が全部開いていると縦に長すぎて、編集している場所を見失う。
   */
  let openTrack = $state<number | null>(null);

  const toggleTrack = (index: number): void => {
    openTrack = openTrack === index ? null : index;
  };

  /**
   * 開く操作の名。
   *
   * 何番目の行なのかを名に持たせる。印だけの操作が縦に並ぶと、**どの行を開くのかが押す前に読めない**
   * ——読み上げでは印そのものが読めない。
   */
  const toggleLabelOf = (index: number): string =>
    shownTrack === index ? `${String(index + 1)}曲目を畳む` : `${String(index + 1)}曲目を開く`;

  const withTrack = (index: number, draft: TrackDraft): void => {
    onChange(tracks.map((track, position) => (position === index ? draft : track)));
  };

  /**
   * 足した行はそのまま開く。足した直後に書き始められないと、開く操作がもう1回要る。
   *
   * 開く位置は**渡す前に**控える。`tracks` は渡した時点で新しい並びを指すため、後から数えると1つ先を
   * 指してしまう。
   */
  const addTrack = (): void => {
    const added = tracks.length;

    onChange([...tracks, EMPTY_TRACK]);
    openTrack = added;
  };

  /** 外した後は畳む。位置がずれるため、開いたままにすると別の行が開いて見える */
  const removeTrack = (index: number): void => {
    onChange(tracks.filter((track, position) => position !== index));
    openTrack = null;
  };

  /**
   * 入れ替えた並び。
   *
   * 番号を持たないため、配列の要素を入れ替えるだけで済む（詰め直しも振り直しも要らない）。
   */
  const swapped = (index: number, other: number): readonly TrackDraft[] =>
    tracks.map((track, position) =>
      position === index
        ? (tracks[other] ?? track)
        : position === other
          ? (tracks[index] ?? track)
          : track,
    );

  /** 動かした行は追う。開いたまま動かすと、開いて見える行が入れ替わる */
  const move = (index: number, other: number): void => {
    onChange(swapped(index, other));
    openTrack = openTrack === index ? other : openTrack;
  };

  const hasErrorAt = (index: number): boolean =>
    [
      ...TRACK_FIELDS.map((field) => trackPathOf(index, field)),
      ...(tracks[index]?.tunes ?? []).flatMap((_tune, tuneIndex) =>
        TUNE_FIELDS.map((field) => tunePathOf(index, tuneIndex, field)),
      ),
    ].some((path) => messagesOf(path).length > 0);

  /** 断られた行。畳んだままでは理由が見えないため、畳む操作より優先して開く */
  const rejectedTrack = $derived(
    tracks.map((track, index) => index).find((index) => hasErrorAt(index)) ?? null,
  );

  /** いま開いている行。誤りがあればその行で、無ければ人が開いた行 */
  const shownTrack = $derived(rejectedTrack ?? openTrack);

  const last = $derived(tracks.length - 1);
</script>

<section class="max-w-2xl space-y-4">
  <h2 class="text-base font-medium">曲目</h2>
  <p class="text-muted-foreground text-sm">
    追加・取り外し・並べ替えは、作品を保存したときに反映されます。
  </p>

  {#if tracks.length === 0}
    <p class="text-muted-foreground text-sm">曲目はありません。</p>
  {:else}
    <ul class="divide-border divide-y" data-tracks>
      <!--
        ROW-KEYED-BY-POSITION: 足したばかりの行はIDを持たない。並びの中で一意に決まるのは位置だけである。
      -->
      {#each tracks as track, index (index)}
        <li class="space-y-2 py-2">
          <div class="flex items-start gap-2">
            <!--
              DISCLOSURE-CHEVRON: 開く操作は行の先頭に置いた印で表す。畳んだ行がいくつも並ぶため、
              文言のボタンを行ごとに置くと、その行が何なのかより「開く」の方が目に入る。

              何番目の行なのかは `aria-label` が持つ。印だけでは、読み上げでどの行を開くのかが読めない。
            -->
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              {disabled}
              aria-expanded={shownTrack === index}
              aria-label={toggleLabelOf(index)}
              onclick={() => {
                toggleTrack(index);
              }}
            >
              <!-- 印の向きが開閉を表す。開いた行では右向きが下を向く -->
              <ChevronRightIcon
                class="transition-transform {shownTrack === index ? 'rotate-90' : ''}"
              />
            </Button>

            <span class="text-muted-foreground w-6 shrink-0 text-right tabular-nums">
              {index + 1}
            </span>

            <div class="min-w-0 flex-1 space-y-1">
              <div class="text-sm" data-track-title>
                {trackTitleOf(track)}
                {#if track.artistDisplayName !== ''}
                  <span class="text-muted-foreground"> / {track.artistDisplayName}</span>
                {/if}
              </div>

              {#if track.tunes.length > 0}
                <ol class="text-muted-foreground space-y-0.5 text-sm" data-track-tunes>
                  {#each track.tunes as tune, tuneIndex (tuneIndex)}
                    <li>{tuneSummaryOf(tune)}</li>
                  {/each}
                </ol>
              {/if}
            </div>

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
                removeTrack(index);
              }}
            >
              外す
            </Button>
          </div>

          <!-- 開いた行だけが入力に変わる。畳んだままの行は読む形のまま残る -->
          {#if shownTrack === index}
            <AlbumTrackEditor
              trackIndex={index}
              draft={track}
              {disabled}
              {messagesOf}
              onDraft={(draft: TrackDraft) => {
                withTrack(index, draft);
              }}
            />
          {/if}
        </li>
      {/each}
    </ul>
  {/if}

  <Button type="button" size="sm" variant="outline" {disabled} onclick={addTrack}>
    トラックを追加する
  </Button>
</section>
