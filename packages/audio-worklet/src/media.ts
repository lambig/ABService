/* eslint-disable functional/immutable-data -- Web Audio graph, epoch and disposal are confined to this media adapter. */
import { audioFeatures } from "abservice-audio-dsp";
import type { AudioFeatures } from "abservice-audio-dsp";
import { meanLoad } from "./accumulator";
import processorUrl from "./features-processor.ts?worker&url";

/** 解析を失っても音声の直接出力は維持し、障害を表示側へ通知する。 */
export type MediaAnalysisOptions = Readonly<{
  onFeatures: (features: AudioFeatures) => void;
  onReset: () => void;
  onError: (error: Error) => void;
  /** 通知の区間の Worklet の平均負荷率（`meanLoad`）。性能の計測でだけ使う。 */
  onLoad?: (load: number) => void;
}>;

/**
 * 出力の途切れと遅延の累計（`AudioContext.playbackStats`）。秒単位。
 * 対応していないブラウザ、または再生を始める前は null。
 */
export type AudioPlaybackStats = Readonly<{
  underrunDuration: number;
  underrunEvents: number;
  totalDuration: number;
  averageLatency: number;
  minimumLatency: number;
  maximumLatency: number;
}>;

const playbackStats = (
  context: AudioContext | null,
): AudioPlaybackStats | null => {
  /* Not in the DOM types yet; browsers without it return undefined. */
  const stats =
    context === null
      ? undefined
      : (Reflect.get(context, "playbackStats") as
          AudioPlaybackStats | undefined);
  return stats === undefined
    ? null
    : Object.freeze({
        underrunDuration: stats.underrunDuration,
        underrunEvents: stats.underrunEvents,
        totalDuration: stats.totalDuration,
        averageLatency: stats.averageLatency,
        minimumLatency: stats.minimumLatency,
        maximumLatency: stats.maximumLatency,
      });
};

/** 借用したmediaとsignalに寿命を合わせる。playはユーザー操作の同一呼び出し内で行う。 */
export const connectMediaAnalysis = (
  media: HTMLAudioElement,
  signal: AbortSignal,
  options: MediaAnalysisOptions,
): Readonly<{
  /** Settles when analysis is prepared, or direct-playback fallback is ready. */
  ready: Promise<void>;
  play: () => void;
  pause: () => void;
  seek: () => void;
  stats: () => AudioPlaybackStats | null;
  /** 解析している AudioContext の sampleRate。接続を作れない場合は undefined。 */
  sampleRate: () => number | undefined;
}> => {
  const preparation = { finish: (): void => {}, timer: 0 };
  const ready = new Promise<void>((resolve) => {
    preparation.finish = resolve;
  });
  const prepared = (): void => {
    clearTimeout(preparation.timer);
    preparation.finish();
  };
  const state: {
    context: AudioContext | null;
    source: MediaElementAudioSourceNode | null;
    node: AudioWorkletNode | null;
    active: boolean;
    failed: boolean;
    epoch: number;
  } = {
    context: null,
    source: null,
    node: null,
    active: false,
    failed: false,
    epoch: 0,
  };
  const clearNode = (): void => {
    state.node?.port.close();
    state.node?.disconnect();
    state.node = null;
  };
  const fail = (error: unknown): void => {
    const report = (): void => {
      state.failed = true;
      clearNode();
      prepared();
      options.onReset();
      options.onError(
        error instanceof Error ? error : new Error(String(error)),
      );
    };
    (signal.aborted ? () => undefined : report)();
  };
  const reset = (): void => {
    state.epoch += 1;
    state.node?.port.postMessage({ epoch: state.epoch });
    options.onReset();
  };
  const receive = (event: MessageEvent<unknown>): void => {
    const data = event.data as {
      epoch?: unknown;
      features?: unknown;
      load?: { busyMs?: unknown; frames?: unknown } | null;
    } | null;
    const features = data?.features as AudioFeatures | null | undefined;
    const accept = (): void => {
      const result = audioFeatures(features as AudioFeatures);
      (result.kind === "features"
        ? () => {
            options.onFeatures(result.features);
          }
        : () => undefined)();
      const load = meanLoad(
        Number(data?.load?.busyMs),
        Number(data?.load?.frames),
        state.context?.sampleRate ?? 0,
      );
      (load === undefined ? () => undefined : () => options.onLoad?.(load))();
    };
    const blocked = [
      signal.aborted,
      state.failed,
      media.paused,
      media.seeking,
    ].some(Boolean);
    ((blocked ? false : state.active) &&
      data?.epoch === state.epoch &&
      typeof features === "object" &&
      features !== null
      ? accept
      : () => undefined)();
  };
  const initialize = (): void => {
    /* Media playback allows buffering headroom for the Worklet output path.
     * The browser chooses the actual latency; observe it through playbackStats. */
    const context = new AudioContext({ latencyHint: "balanced" });
    state.context = context;
    const source = context.createMediaElementSource(media);
    state.source = source;
    source.connect(context.destination);
    preparation.timer = window.setTimeout(() => {
      fail(new Error("Audio analysis preparation timed out"));
    }, 10000);
    /* Load and exercise DSP while suspended, before media.play can advance the source. */
    void context
      .suspend()
      .then(() =>
        [signal.aborted, state.failed].some(Boolean)
          ? undefined
          : context.audioWorklet.addModule(processorUrl),
      )
      .then(() => {
        const attach = (): void => {
          const node = new AudioWorkletNode(context, "abservice-features", {
            numberOfInputs: 1,
            numberOfOutputs: 1,
            outputChannelCount: [1],
            channelCountMode: "max",
            channelInterpretation: "discrete",
            processorOptions: { notificationHz: 30 },
          });
          state.node = node;
          node.port.onmessage = (event: MessageEvent<unknown>): void => {
            const data = event.data as { kind?: unknown } | null;
            ((
              [signal.aborted, state.failed].some(Boolean)
                ? false
                : data?.kind === "ready"
            )
              ? () => {
                  node.port.onmessage = receive;
                  /* The analysis output is silence; only the direct branch is audible. */
                  source.connect(node).connect(context.destination);
                  prepared();
                }
              : () => {
                  receive(event);
                })();
          };
          node.onprocessorerror = () => {
            fail(new Error("Audio analysis processor failed"));
          };
          node.port.postMessage({ epoch: state.epoch });
        };
        ([signal.aborted, state.failed].some(Boolean)
          ? () => undefined
          : attach)();
      })
      .catch(fail);
  };
  const play = (): void => {
    const start = (): void => {
      state.active = true;
      reset();
      try {
        void state.context?.resume().catch(fail);
      } catch (error) {
        fail(error);
      }
    };
    (signal.aborted ? () => undefined : start)();
  };
  const pause = (): void => {
    state.active = false;
    reset();
    const context = state.context;
    (context === null
      ? () => undefined
      : () => {
          void context.suspend().catch(fail);
        })();
  };
  const dispose = (): void => {
    state.active = false;
    clearNode();
    prepared();
    state.source?.disconnect();
    const context = state.context;
    state.context = null;
    (context === null
      ? () => undefined
      : () => {
          void context.close().catch(() => undefined);
        })();
  };
  signal.addEventListener("abort", dispose, { once: true });
  media.addEventListener("seeking", reset, { signal });
  media.addEventListener("seeked", reset, { signal });
  try {
    (signal.aborted ? prepared : initialize)();
  } catch (error) {
    fail(error);
  }
  return Object.freeze({
    ready,
    play,
    pause,
    seek: reset,
    stats: () => playbackStats(state.context),
    sampleRate: () => state.context?.sampleRate,
  });
};
