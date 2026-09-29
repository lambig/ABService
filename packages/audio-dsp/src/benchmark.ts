/* eslint-disable functional/immutable-data -- The benchmark carries the stream state and the timings across quanta in one pass. */
import { createFeatureStream } from "./features";
import { defaultSpectralConfig } from "./spectral";
import type { SpectralConfig } from "./spectral";
import type { FeatureStream } from "./features";

/**
 * quantum ごとの処理時間と、quantum の長さに対する余裕。
 * AudioWorklet の中には精度のある時計が無いため、同じ DSP を同じ入力で別スレッドから測った近似として使う。
 * - quantumMs: 1 quantum の音声の長さ（この時間内に処理を終える必要がある）
 * - p50Ms / p95Ms / maxMs: quantum ごとの処理時間の分布
 * - p95Ratio / maxRatio: 処理時間の quantum に対する割合。1 に達すると間に合わない
 */
export type QuantumBenchmark = Readonly<{
  quanta: number;
  quantumFrames: number;
  quantumMs: number;
  p50Ms: number;
  p95Ms: number;
  maxMs: number;
  p95Ratio: number;
  maxRatio: number;
}>;

export type BenchmarkOptions = Readonly<{
  sampleRate: number;
  /** 高分解能の時計。ブラウザでは performance.now。 */
  now: () => number;
  /** Web Audio の render quantum。既定は 128 フレーム。 */
  quantumFrames?: number;
  /** 測る quantum 数の上限。長い音源でも測定を短く保つ。 */
  maxQuanta?: number;
  config?: SpectralConfig;
}>;

const percentile = (sorted: readonly number[], fraction: number): number =>
  sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * fraction))] ??
  0;

/**
 * 同じ DSP（`createFeatureStream`）へ PCM を quantum ずつ渡し、1 quantum ごとの処理時間を測る。
 * Worklet と同じく、特徴量の生成まで含めて測る。PCM は借用するだけで保持しない。
 */
export const benchmarkFeatureStream = (
  channels: readonly Float32Array[],
  options: BenchmarkOptions,
): QuantumBenchmark => {
  const quantumFrames = options.quantumFrames ?? 128;
  const length = channels[0]?.length ?? 0;
  const quanta = Math.min(
    Math.floor(length / quantumFrames),
    options.maxQuanta ?? Number.MAX_SAFE_INTEGER,
  );
  const state: { stream: FeatureStream } = {
    stream: createFeatureStream({
      ...(options.config ?? defaultSpectralConfig),
      sampleRate: options.sampleRate,
    }),
  };
  const durations = Array.from({ length: quanta }, (_, index) => {
    const start = index * quantumFrames;
    const block = {
      channels: channels.map((channel) =>
        channel.subarray(start, start + quantumFrames),
      ),
      sampleRate: options.sampleRate,
      timeSeconds: start / options.sampleRate,
    };
    const begin = options.now();
    state.stream = state.stream.push(block).next;
    return options.now() - begin;
  });
  const sorted = [...durations].sort((left, right) => left - right);
  const quantumMs = (quantumFrames / options.sampleRate) * 1000;
  const p95Ms = percentile(sorted, 0.95);
  const maxMs = sorted.at(-1) ?? 0;
  return Object.freeze({
    quanta,
    quantumFrames,
    quantumMs,
    p50Ms: percentile(sorted, 0.5),
    p95Ms,
    maxMs,
    p95Ratio: p95Ms / quantumMs,
    maxRatio: maxMs / quantumMs,
  });
};
