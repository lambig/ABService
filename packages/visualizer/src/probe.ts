/* eslint-disable functional/immutable-data -- The probe keeps bounded rings of recent samples for one page. */

/**
 * renderer の 1 フレームの観測。CPU と GPU を分けて記録する（どちらが律速かで対策が変わるため）。
 * - rendererCpuMs: renderer の中で描画値を書き、命令を作って submit するまで（WebGPU の命令作成の CPU コスト）
 * - gpuMs: submit から GPU がその仕事を終えたと通知するまで（queue の待ちを含む上限値）
 * - intervalMs: 前のフレームからの rAF の間隔
 * - pixels: 描画寸法の画素数
 */
export type FrameSample = Readonly<{
  rendererCpuMs: number;
  gpuMs?: number;
  intervalMs?: number;
  pixels: number;
}>;

export type Distribution = Readonly<{ p50: number; p95: number; max: number }>;

/**
 * 観測の要約。
 * - rendererCpu: renderer の中の CPU 時間
 * - cycleCpu: 1 回の描画までに main thread が使った時間の合計（特徴量から描画値への変換・状態・DOM 更新・renderer を含む）。
 *   worker・GPU 寄せ・WASM の要否はこちらで判断する
 */
export type ProbeSummary = Readonly<{
  frames: number;
  rendererCpu?: Distribution;
  cycleCpu?: Distribution;
  gpu?: Distribution;
  interval?: Distribution;
  pixels?: number;
}>;

export type FrameProbe = Readonly<{
  record: (sample: FrameSample) => void;
  /** 1 回の描画までの main thread の時間。呼び出し側が測って渡す。 */
  cycle: (cpuMs: number) => void;
  /** 描画を見送ったフレームは record せず、ここで数える。 */
  skip: () => void;
  summary: () => ProbeSummary & Readonly<{ skipped: number }>;
}>;

const percentile = (sorted: readonly number[], fraction: number): number =>
  sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * fraction))] ??
  0;
/** 値の分布。値が無ければ undefined。 */
export const distribution = (
  values: readonly number[],
): Distribution | undefined => {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted.length === 0
    ? undefined
    : {
        p50: percentile(sorted, 0.5),
        p95: percentile(sorted, 0.95),
        max: sorted[sorted.length - 1] ?? 0,
      };
};
const defined = (values: readonly (number | undefined)[]): readonly number[] =>
  values.filter((value): value is number => value !== undefined);
const keep = <T>(ring: T[], value: T, capacity: number): void => {
  ring.push(value);
  ring.splice(0, Math.max(0, ring.length - capacity));
};

/** 直近 capacity フレームだけを持つ観測。長時間の稼働でも記憶が増え続けない。 */
export const createProbe = (capacity = 600): FrameProbe => {
  const samples: FrameSample[] = [];
  const cycles: number[] = [];
  const counts = { skipped: 0 };
  return Object.freeze({
    record: (sample: FrameSample): void => {
      keep(samples, sample, capacity);
    },
    cycle: (cpuMs: number): void => {
      keep(cycles, cpuMs, capacity);
    },
    skip: (): void => {
      counts.skipped += 1;
    },
    summary: () => {
      const rendererCpu = distribution(
        samples.map((sample) => sample.rendererCpuMs),
      );
      const cycleCpu = distribution(cycles);
      const gpu = distribution(defined(samples.map((sample) => sample.gpuMs)));
      const interval = distribution(
        defined(samples.map((sample) => sample.intervalMs)),
      );
      const pixels = samples.at(-1)?.pixels;
      return Object.freeze({
        frames: samples.length,
        skipped: counts.skipped,
        ...(rendererCpu === undefined ? {} : { rendererCpu }),
        ...(cycleCpu === undefined ? {} : { cycleCpu }),
        ...(gpu === undefined ? {} : { gpu }),
        ...(interval === undefined ? {} : { interval }),
        ...(pixels === undefined ? {} : { pixels }),
      });
    },
  });
};
