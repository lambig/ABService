import type { ListeningAlbum } from "abservice-listening-presentation";
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
 * 再生の観測。player の状態（`abservice-player` の `PlayerSnapshot`）が構造的に満たす形だけを受け取る。
 * visualizer は player に依存しない。シーク中（`seeking`）だけが戻り先 `resumeTo` を持つ。
 */
export type PlaybackObservation = Readonly<{
  phase:
    | "idle"
    | "loading"
    | "ready"
    | "playing"
    | "paused"
    | "ended"
    | "error"
    | "seeking";
  resumeTo?: "playing" | "paused";
  playbackItemId: string | null;
}>;

type Settled = Exclude<PlaybackObservation["phase"], "seeking">;
/* ready follows a select that already moved to selected; it adds no event of its own. */
const settledEvents: Readonly<Record<Settled, readonly PresentationEvent[]>> = {
  idle: [],
  loading: [{ kind: "select" }],
  ready: [],
  playing: [{ kind: "play" }],
  paused: [{ kind: "pause" }],
  ended: [{ kind: "end" }],
  error: [{ kind: "fail" }],
};
const settledOf = (
  observation: PlaybackObservation,
): readonly PresentationEvent[] =>
  observation.phase === "seeking"
    ? []
    : observation.phase === "idle"
      ? [
          observation.playbackItemId === null
            ? { kind: "clear" }
            : { kind: "stop" },
        ]
      : settledEvents[observation.phase];

/**
 * 再生の観測の前後から、presentation state の出来事を作る純粋な関数。
 * - 新しい読み込み（別の項目、または loading への移り）は select
 * - 停止して選択が残れば stop、選択が無ければ clear
 * - seeking に入れば seek、出れば出た先の再生の有無を持つ seeked
 * - 変化の無い観測（位置の更新だけ等）からは出来事を作らない
 */
export const playbackEvents = (
  previous: PlaybackObservation | undefined,
  next: PlaybackObservation,
): readonly PresentationEvent[] => {
  const changed = [
    previous?.phase !== next.phase,
    previous?.playbackItemId !== next.playbackItemId,
  ].some(Boolean);
  const leftSeek = previous?.phase === "seeking" && next.phase !== "seeking";
  return changed
    ? next.phase === "seeking"
      ? previous?.phase === "seeking"
        ? []
        : [{ kind: "seek" }]
      : leftSeek
        ? [
            { kind: "seeked", playing: next.phase === "playing" },
            ...(["playing", "paused"].includes(next.phase) ? [] : settledOf(next)),
          ]
        : settledOf(next)
    : [];
};

/** 再生の観測の変化で presentation state を進める。途中の出来事のどれかが描画値を戻せば resetFrame。 */
export const followPlayback = (
  state: PresentationState,
  previous: PlaybackObservation | undefined,
  next: PlaybackObservation,
): PresentationTransition =>
  Object.freeze(
    playbackEvents(previous, next).reduce<PresentationTransition>(
      (acc, event) => {
        const step = transition(acc.state, event);
        return {
          state: step.state,
          resetFrame: [acc.resetFrame, step.resetFrame].some(Boolean),
        };
      },
      { state, resetFrame: false },
    ),
  );

/**
 * 作品の表示内容。検証済みの表示データ（#476の PresentationData）の作品をそのまま渡す。
 * 作品の事実をすべて持ち、どれをどう見せるかは renderer の側が決める。
 * 値の無い項目は省かれており、placeholder を事実として渡さない。
 */
export type PresentationContent = ListeningAlbum;

/**
 * renderer へ渡す入力。renderer は音響特徴量・Manifest・player を参照せず、これだけで描く。
 * 状態と表示内容の組み合わせは型で決まる。
 * - idle: 作品を選んでいないので表示内容を持たない
 * - error: 作品を選ぶ前にも失敗しうるので、表示内容は任意
 * - それ以外: 作品を選んでいるので表示内容を必ず持つ
 */
export type PresentationInput =
  | Readonly<{ state: "idle"; frame: PresentationFrame }>
  | Readonly<{
      state: "error";
      frame: PresentationFrame;
      content?: PresentationContent;
    }>
  | Readonly<{
      state: Exclude<PresentationState, "idle" | "error">;
      frame: PresentationFrame;
      content: PresentationContent;
    }>;
