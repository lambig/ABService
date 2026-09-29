/* eslint-disable functional/immutable-data -- The audio probe keeps bounded rings, the current connection and the last playback stats for one page. */
import type { AudioFeatures, QuantumBenchmark } from "abservice-audio-dsp";
import type { AudioPlaybackStats } from "abservice-audio-worklet/media";
import { distribution } from "abservice-visualizer";
import type { BenchmarkRequest } from "./benchmark-worker";

/** 観測する接続。`connectMediaAnalysis` の戻り値の一部。 */
export type AudioConnection = Readonly<{
  stats: () => AudioPlaybackStats | null;
  sampleRate: () => number | undefined;
}>;

/** ベンチマークの状態。main thread の decode を伴うため、画面の操作で 1 回ずつ走らせる。 */
export type BenchmarkState =
  | Readonly<{ kind: "idle" }>
  | Readonly<{ kind: "running" }>
  | Readonly<{ kind: "done"; result: QuantumBenchmark; seconds: number }>
  | Readonly<{ kind: "failed"; message: string }>;

/* A benchmark over half a minute of audio is long enough for a stable p95 and short enough to finish quickly. */
const benchmarkSeconds = 30;
const renderQuantum = 128;

const keep = (ring: number[], value: number, capacity: number): void => {
  ring.push(value);
  ring.splice(0, Math.max(0, ring.length - capacity));
};

/**
 * 保存済みの音源を main thread で decode し、チャンネルの配列をコピーせずに worker へ移してベンチマークする。
 * worker には `decodeAudioData` が無いため decode だけ main thread で行う（#290 の計測契約）。
 */
export const benchmarkAudio = async (
  audio: Blob,
  sampleRate: number,
): Promise<Readonly<{ result: QuantumBenchmark; seconds: number }>> => {
  const decoded = await new OfflineAudioContext(1, 1, sampleRate).decodeAudioData(
    await audio.arrayBuffer(),
  );
  const channels = Array.from({ length: decoded.numberOfChannels }, (_, index) =>
    decoded.getChannelData(index),
  );
  const request: BenchmarkRequest = {
    channels,
    sampleRate,
    maxQuanta: Math.ceil((benchmarkSeconds * sampleRate) / renderQuantum),
  };
  const worker = new Worker(new URL("./benchmark-worker.ts", import.meta.url), {
    type: "module",
  });
  const reply = await new Promise<
    | Readonly<{ kind: "result"; result: QuantumBenchmark }>
    | Readonly<{ kind: "error"; message: string }>
  >((resolve) => {
    worker.addEventListener("message", (event) => {
      resolve(event.data as Parameters<typeof resolve>[0]);
    });
    worker.addEventListener("error", (event) => {
      resolve({ kind: "error", message: event.message });
    });
    worker.postMessage(
      request,
      channels.map((channel) => channel.buffer),
    );
  });
  worker.terminate();
  return reply.kind === "result"
    ? Object.freeze({
        result: reply.result,
        seconds: (reply.result.quanta * reply.result.quantumMs) / 1000,
      })
    : Promise.reject(new Error(reply.message));
};

/**
 * 音声側の観測（#290 の計測契約）。`#probe` のときだけ作る。
 * - 通知: 件数と、AudioContext の時刻での通知間隔
 * - Worklet の平均負荷率: 通知の区間ごとの値の分布
 * - 音切れと遅延: `playbackStats`。接続を捨てる直前の値を残し、最後の区間を取りこぼさない
 */
export const createAudioProbe = (capacity = 600) => {
  const loads: number[] = [];
  const intervals: number[] = [];
  const state: {
    notifications: number;
    lastTime: number | undefined;
    connection: AudioConnection | undefined;
    stats: AudioPlaybackStats | null;
    sampleRate: number | undefined;
    benchmark: BenchmarkState;
  } = {
    notifications: 0,
    lastTime: undefined,
    connection: undefined,
    stats: null,
    sampleRate: undefined,
    benchmark: { kind: "idle" },
  };
  const snapshot = (): void => {
    const connection = state.connection;
    state.stats = connection?.stats() ?? state.stats;
    state.sampleRate = connection?.sampleRate() ?? state.sampleRate;
  };
  return Object.freeze({
    features: (features: AudioFeatures): void => {
      state.notifications += 1;
      (state.lastTime === undefined
        ? () => undefined
        : () => {
            keep(
              intervals,
              (features.timeSeconds - (state.lastTime as number)) * 1000,
              capacity,
            );
          })();
      state.lastTime = features.timeSeconds;
    },
    /* Pause, seek and reselection break the time line; the next interval starts afresh. */
    reset: (): void => {
      state.lastTime = undefined;
    },
    load: (value: number): void => {
      keep(loads, value, capacity);
    },
    /**
     * 接続の寿命を見張る。`connectMediaAnalysis` より先に呼び、その abort より先に最新の値を写す。
     * 戻り値へ接続を渡す。
     */
    watch: (signal: AbortSignal): ((connection: AudioConnection) => void) => {
      signal.addEventListener(
        "abort",
        () => {
          snapshot();
          state.connection = undefined;
        },
        { once: true },
      );
      return (connection) => {
        state.connection = signal.aborted ? undefined : connection;
      };
    },
    sampleRate: (): number | undefined => {
      snapshot();
      return state.sampleRate;
    },
    benchmark: (next: BenchmarkState): void => {
      state.benchmark = next;
    },
    benchmarking: (): boolean => state.benchmark.kind === "running",
    report: (): readonly string[] => {
      snapshot();
      const stats = state.stats;
      const rate = state.sampleRate;
      const interval = distribution(intervals);
      const load = distribution(loads);
      const percent = (value: number): string => `${(value * 100).toFixed(1)}%`;
      const bench = state.benchmark;
      return [
        rate === undefined
          ? "audio -"
          : `audio sampleRate ${String(rate)} quantum ${((renderQuantum / rate) * 1000).toFixed(2)} ms`,
        `notifications ${String(state.notifications)}${
          interval === undefined
            ? ""
            : ` interval p50 ${interval.p50.toFixed(1)} / max ${interval.max.toFixed(1)} ms`
        }`,
        load === undefined
          ? "worklet load -"
          : `worklet load p50 ${percent(load.p50)} / p95 ${percent(load.p95)} / max ${percent(load.max)}`,
        stats === null
          ? "underrun -"
          : `underrun ${String(stats.underrunEvents)} events ${stats.underrunDuration.toFixed(3)} s / ${stats.totalDuration.toFixed(1)} s · latency avg ${(stats.averageLatency * 1000).toFixed(1)} / max ${(stats.maximumLatency * 1000).toFixed(1)} ms`,
        bench.kind === "idle"
          ? "dsp bench -"
          : bench.kind === "running"
            ? "dsp bench running"
            : bench.kind === "failed"
              ? `dsp bench failed: ${bench.message}`
              : `dsp bench ${String(bench.result.quanta)} quanta (${bench.seconds.toFixed(1)} s) p50 ${bench.result.p50Ms.toFixed(3)} / p95 ${bench.result.p95Ms.toFixed(3)} / max ${bench.result.maxMs.toFixed(3)} ms · p95 ${percent(bench.result.p95Ratio)} max ${percent(bench.result.maxRatio)} of quantum`,
      ];
    },
  });
};

export type AudioProbe = ReturnType<typeof createAudioProbe>;
