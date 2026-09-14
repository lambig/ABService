<script lang="ts">
  import { Button } from '$components/ui/button/index.js';
  import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
  import {
    EMPTY_TUNE,
    TUNE_FIELDS,
    trackPathOf,
    tunePathOf,
    tuneSummaryOf,
    type TrackDraft,
    type TuneField,
  } from '$lib/api/track-form';

  /**
   * 曲目1行の入力。
   *
   * <p>
   * ここが持つのは入力だけで、送るのは作品の保存である（#391）。追加も編集も同じ形で、押した時点では
   * 何も起きない。
   * </p>
   *
   * <p>
   * 行の誤りは**その行の欄の下**に出す。管理APIは位置を `tracks[0].tunes[1].linkUrl` の形で返し、画面は
   * その綴りで欄を持つ（#288）。位置を捨てると、どの行が不正なのかを読み手に伝えられない。
   * </p>
   */
  type Props = {
    /** 作品の中でのこの行の位置。誤りの位置を組み立てるのに要る */
    readonly trackIndex: number;
    readonly draft: TrackDraft;
    /** 入力を触らせない間（保存中・画像の送信中） */
    readonly disabled: boolean;
    /** 位置に割り当てられた誤り */
    readonly messagesOf: (path: string) => readonly string[];
    readonly onDraft: (draft: TrackDraft) => void;
  };

  const { trackIndex, draft, disabled, messagesOf, onDraft }: Props = $props();

  const trackMessages = (field: 'title' | 'artistDisplayName' | 'artistSortKey') =>
    messagesOf(trackPathOf(trackIndex, field));

  const tuneMessages = (tuneIndex: number, field: TuneField) =>
    messagesOf(tunePathOf(trackIndex, tuneIndex, field));

  const withTune = (index: number, field: TuneField, value: string): void => {
    onDraft({
      ...draft,
      tunes: draft.tunes.map((tune, position) =>
        position === index ? { ...tune, [field]: value } : tune,
      ),
    });
  };

  /**
   * 開いているチューンの行。
   *
   * <p>
   * <b>一度に開くのは1行だけ。</b> 全部を開くと、1行あたり3〜4つの欄が縦に積まれて、編集している場所を
   * 見失う。畳んだ行は出る形（公開サイトと同じ読み方）で並び、開いた行だけが入力に変わる。
   * </p>
   *
   * <p>
   * 畳んでも書きかけは消えない——入力は作品の下書きが持っており、この状態が持つのは<b>見え方だけ</b>である。
   * </p>
   */
  let openTune = $state<number | null>(null);

  const toggleTune = (index: number): void => {
    openTune = openTune === index ? null : index;
  };

  /**
   * 開く操作の名。
   *
   * 何チューン目かを名に持たせる。印だけの操作が縦に並ぶと、**どの行を開くのかが押す前に読めない**
   * ——読み上げでは印そのものが読めない。
   */
  const toggleLabelOf = (index: number): string =>
    shownTune === index
      ? `${String(index + 1)}チューン目を畳む`
      : `${String(index + 1)}チューン目を開く`;

  /**
   * 足した行はそのまま開く。足した直後に書き始められないと、開く操作がもう1回要る。
   *
   * 開く位置は**渡す前に**控える。`draft` は渡した時点で新しい並びを指すため、後から数えると1つ先を
   * 指してしまう。
   */
  const addTune = (): void => {
    const added = draft.tunes.length;

    onDraft({ ...draft, tunes: [...draft.tunes, EMPTY_TUNE] });
    openTune = added;
  };

  /** 外した後は畳む。位置がずれるため、開いたままにすると別の行が開いて見える */
  const removeTune = (index: number): void => {
    onDraft({ ...draft, tunes: draft.tunes.filter((tune, position) => position !== index) });
    openTune = null;
  };

  /** 欄の名 */
  const TUNE_LABELS = {
    tuneTitle: '曲名',
    composerCreditOverride: '作曲',
    arrangerCreditOverride: '編曲',
    linkUrl: 'リンクURL',
  } as const satisfies Record<TuneField, string>;

  /**
   * 行ごとの欄の名。
   *
   * <p>
   * 何チューン目の欄なのかを名に持たせる。「曲名」「作曲」だけが縦に並ぶと、<b>どの行を触っているのかが
   * 欄からは読めない</b>——トラックの欄との区別も付かない。
   * </p>
   */
  const labelOf = (index: number, field: TuneField): string =>
    `${String(index + 1)}チューン目の${TUNE_LABELS[field]}`;

  /** 欄の識別子。同時に開く行は1つだが、作品の中で一意にする */
  const tuneIdOf = (index: number, field: TuneField): string =>
    `track-${String(trackIndex)}-tune-${String(index)}-${field}`;

  const trackIdOf = (field: string): string => `track-${String(trackIndex)}-${field}`;

  /** 横に並べる欄。対になって読まれるものを1行に置く */
  const TUNE_ROWS: readonly (readonly TuneField[])[] = [
    ['tuneTitle'],
    ['composerCreditOverride', 'arrangerCreditOverride'],
    ['linkUrl'],
  ];

  const hasErrorAt = (index: number): boolean =>
    TUNE_FIELDS.some((field) => tuneMessages(index, field).length > 0);

  /** 断られた行。畳んだままでは理由が見えないため、畳む操作より優先して開く */
  const rejectedTune = $derived(
    draft.tunes.map((tune, index) => index).find((index) => hasErrorAt(index)) ?? null,
  );

  /** いま開いている行。誤りがあればその行で、無ければ人が開いた行 */
  const shownTune = $derived(rejectedTune ?? openTune);
</script>

<div class="border-input space-y-4 rounded-md border p-3" data-track-editor>
  <div class="space-y-1" data-field={trackPathOf(trackIndex, 'title')}>
    <label class="text-sm font-medium" for={trackIdOf('title')}>
      トラック名（省くとチューン名から組まれます）
    </label>
    <input
      id={trackIdOf('title')}
      class="border-input bg-background w-full rounded-md border px-3 py-2"
      type="text"
      value={draft.title}
      {disabled}
      aria-invalid={trackMessages('title').length > 0}
      oninput={(event) => {
        onDraft({ ...draft, title: event.currentTarget.value });
      }}
    />
    {#each trackMessages('title') as message (message)}
      <p class="text-destructive text-sm" role="alert">{message}</p>
    {/each}
  </div>

  <!-- 名義とそのソートキーは対で読まれる。縦に積むと、どれとどれが組なのかが読めない -->
  <div class="flex flex-wrap items-end gap-4">
    <div
      class="min-w-40 flex-1 space-y-1"
      data-field={trackPathOf(trackIndex, 'artistDisplayName')}
    >
      <label class="text-sm font-medium" for={trackIdOf('artist')}>
        名義（作品の名義と違うときだけ）
      </label>
      <input
        id={trackIdOf('artist')}
        class="border-input bg-background w-full rounded-md border px-3 py-2"
        type="text"
        value={draft.artistDisplayName}
        {disabled}
        aria-invalid={trackMessages('artistDisplayName').length > 0}
        oninput={(event) => {
          onDraft({ ...draft, artistDisplayName: event.currentTarget.value });
        }}
      />
      {#each trackMessages('artistDisplayName') as message (message)}
        <p class="text-destructive text-sm" role="alert">{message}</p>
      {/each}
    </div>

    <div class="min-w-40 flex-1 space-y-1" data-field={trackPathOf(trackIndex, 'artistSortKey')}>
      <label class="text-sm font-medium" for={trackIdOf('artist-sort')}>ソートキー</label>
      <input
        id={trackIdOf('artist-sort')}
        class="border-input bg-background w-full rounded-md border px-3 py-2"
        type="text"
        value={draft.artistSortKey}
        {disabled}
        aria-invalid={trackMessages('artistSortKey').length > 0}
        oninput={(event) => {
          onDraft({ ...draft, artistSortKey: event.currentTarget.value });
        }}
      />
      {#each trackMessages('artistSortKey') as message (message)}
        <p class="text-destructive text-sm" role="alert">{message}</p>
      {/each}
    </div>
  </div>

  <div class="space-y-2">
    <h4 class="text-sm font-medium">チューン構成</h4>
    <p class="text-muted-foreground text-sm">
      登場順はこの並びのとおりです。曲名だけの行も置けます。
    </p>

    {#each draft.tunes as tune, index (index)}
      <div class="border-input space-y-2 rounded-md border p-2" data-tune>
        <!--
          COLLAPSED-READS-LIKE-PUBLIC: 畳んだ行は出る形で読む。開く操作に何チューン目かを持たせるのは、
          畳んだ行が並んだときにどれを開くのかを押す前に読めるようにするため。
        -->
        <div class="flex items-center gap-2">
          <!-- 開く操作は行の先頭の印で表す。何チューン目かは `aria-label` が持つ -->
          <Button
            type="button"
            size="icon-sm"
            variant="ghost"
            {disabled}
            aria-expanded={shownTune === index}
            aria-label={toggleLabelOf(index)}
            onclick={() => {
              toggleTune(index);
            }}
          >
            <ChevronRightIcon
              class="transition-transform {shownTune === index ? 'rotate-90' : ''}"
            />
          </Button>

          <span class="min-w-0 flex-1 truncate text-sm" data-tune-summary>
            {tuneSummaryOf(tune)}
          </span>

          <!-- どの行を外すのかを文言に持たせる。「この行」では、押す前に対象が読めない -->
          <Button
            type="button"
            size="sm"
            variant="outline"
            {disabled}
            onclick={() => {
              removeTune(index);
            }}
          >
            {String(index + 1)}チューン目を外す
          </Button>
        </div>

        {#if shownTune === index}
          {#each TUNE_ROWS as row (row[0])}
            <div class="flex flex-wrap items-end gap-4">
              {#each row as field (field)}
                <div
                  class="min-w-40 flex-1 space-y-1"
                  data-field={tunePathOf(trackIndex, index, field)}
                >
                  <label class="text-sm" for={tuneIdOf(index, field)}>
                    {labelOf(index, field)}
                  </label>
                  <input
                    id={tuneIdOf(index, field)}
                    class="border-input bg-background w-full rounded-md border px-3 py-2"
                    type="text"
                    value={tune[field]}
                    {disabled}
                    aria-invalid={tuneMessages(index, field).length > 0}
                    oninput={(event) => {
                      withTune(index, field, event.currentTarget.value);
                    }}
                  />
                  {#each tuneMessages(index, field) as message (message)}
                    <p class="text-destructive text-sm" role="alert">{message}</p>
                  {/each}
                </div>
              {/each}
            </div>
          {/each}
        {/if}
      </div>
    {/each}

    <Button type="button" size="sm" variant="outline" {disabled} onclick={addTune}>
      チューンを足す
    </Button>
  </div>
</div>
