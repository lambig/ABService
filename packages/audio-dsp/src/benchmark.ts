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

const invalid = (message: string): never => {
  throw new RangeError(message);
};
/* A zero quantum or a negative limit would make the quantum count unbounded or negative. */
const checked = (
  options: BenchmarkOptions,
): Readonly<{ quantumFrames: number; maxQuanta: number }> => {
  const quantumFrames = options.quantumFrames ?? 128;
  const maxQuanta = options.maxQuanta ?? Number.MAX_SAFE_INTEGER;
  return [
    Number.isFinite(options.sampleRate) && options.sampleRate > 0
      ? undefined
      : "sampleRate must be a positive finite number",
    Number.isSafeInteger(quantumFrames) && quantumFrames > 0
      ? undefined
      : "quantumFrames must be a positive integer",
    Number.isSafeInteger(maxQuanta) && maxQuanta >= 0
      ? undefined
      : "maxQuanta must be a non-negative integer",
  ]
    .filter((message): message is string => message !== undefined)
    .reduce<Readonly<{ quantumFrames: number; maxQuanta: number }>>(
      (_, message) => invalid(message),
      { quantumFrames, maxQuanta },
    );
};

const percentile = (sorted: readonly number[], fraction: number): number =>
  sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * fraction))] ??
  0;

/**
 * 同じ DSP（`createFeatureStream`）へ PCM を quantum ずつ渡し、1 quantum ごとの処理時間を測る。
 * Worklet と同じく、特徴量の生成まで含めて測る。PCM は借用するだけで保持しない。
 * sampleRate は正の有限値、quantumFrames は正の整数、maxQuanta は 0 以上の整数（0 は測らない）。外れたら RangeError。
 */
export const benchmarkFeatureStream = (
  channels: readonly Float32Array[],
  options: BenchmarkOptions,
): QuantumBenchmark => {
  const { quantumFrames, maxQuanta } = checked(options);
  const length = channels[0]?.length ?? 0;
  const quanta = Math.min(Math.floor(length / quantumFrames), maxQuanta);
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
