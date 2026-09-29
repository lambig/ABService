import type { PresentationFrame } from "./index";

/**
 * 試聴体験の状態の語彙。見せ方（layout・mapping・effect・動きの強さ）は含まない。
 * - idle: 何も選んでいない。音を鳴らさず、作品選択を主にする
 * - selected: 作品を選んだ。再生前・停止後・終了後の戻り先でもある
 * - playing: 再生中。AudioFeatures に反応する
 * - paused: 一時停止。直前の構図を保って静まる
 * - seeking: 再生位置の移動中。旧い反応を残さない
 * - ended: 最後まで再生した
 * - error: 読み込み・再生に失敗した。操作は残す
 */
export type PresentationState =
  | "idle"
  | "selected"
  | "playing"
  | "paused"
  | "seeking"
  | "ended"
  | "error";

/**
 * 状態を動かす出来事。player の状態から写すのは呼び出し側（#476 C後半）。
 * seeked は移動後に再生を続けるかを持つ。
 */
export type PresentationEvent =
  | Readonly<{ kind: "select" }>
  | Readonly<{ kind: "play" }>
  | Readonly<{ kind: "pause" }>
  | Readonly<{ kind: "seek" }>
  | Readonly<{ kind: "seeked"; playing: boolean }>
  | Readonly<{ kind: "end" }>
  | Readonly<{ kind: "stop" }>
  | Readonly<{ kind: "fail" }>
  | Readonly<{ kind: "clear" }>;

/**
 * 遷移の結果。resetFrame が true のときは、描画値を静止値へ戻してから次の特徴量を受け取る。
 * 旧い作品・旧い再生位置の反応（平滑化・impulse）を残さないため。
 */
export type PresentationTransition = Readonly<{
  state: PresentationState;
  resetFrame: boolean;
}>;

type Kind = PresentationEvent["kind"];
/* Events not listed for a state leave it unchanged; an out-of-order notification must not invent a state. */
const table: Readonly<
  Record<PresentationState, Partial<Record<Kind, PresentationState>>>
> = {
  idle: { select: "selected", fail: "error" },
  selected: { select: "selected", play: "playing", fail: "error", clear: "idle" },
  playing: {
    select: "selected",
    pause: "paused",
    seek: "seeking",
    end: "ended",
    stop: "selected",
    fail: "error",
    clear: "idle",
  },
  paused: {
    select: "selected",
    play: "playing",
    seek: "seeking",
    stop: "selected",
    fail: "error",
    clear: "idle",
  },
  seeking: { select: "selected", stop: "selected", fail: "error", clear: "idle" },
  ended: {
    select: "selected",
    play: "playing",
    seek: "seeking",
    stop: "selected",
    fail: "error",
    clear: "idle",
  },
  error: { select: "selected", clear: "idle" },
};
/* Selecting again, moving the position, stopping or failing starts from rest; pausing keeps the composition. */
const resetting: ReadonlySet<Kind> = new Set<Kind>([
  "select",
  "seek",
  "stop",
  "fail",
  "clear",
]);

/** 出来事から次の状態を決める純粋な関数。その状態で起きえない出来事では状態を変えない。 */
export const transition = (
  state: PresentationState,
  event: PresentationEvent,
): PresentationTransition => {
  const next =
    event.kind === "seeked"
      ? state === "seeking"
        ? event.playing
          ? "playing"
          : "paused"
        : undefined
      : table[state][event.kind];
  return Object.freeze({
    state: next ?? state,
    resetFrame: next === undefined ? false : resetting.has(event.kind),
  });
};

/**
 * 作品の表示内容。検証済みの表示データ（#476の PresentationData）から写す。
 * 値の無い項目は省き、placeholder を事実として渡さない。artwork は検証済みの実体。
 */
export type PresentationContent = Readonly<{
  title: string;
  artistDisplayName?: string;
  artwork?: Blob;
}>;

/**
 * renderer へ渡す入力。renderer は音響特徴量・Manifest・player を参照せず、これだけで描く。
 * content が無いのは idle（作品を選んでいない）。
 */
export type PresentationInput = Readonly<{
  state: PresentationState;
  frame: PresentationFrame;
  content?: PresentationContent;
}>;
