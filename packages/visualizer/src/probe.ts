/* eslint-disable functional/immutable-data -- The probe keeps a bounded ring of recent samples for one page. */

/**
 * 1 フレームの観測。CPU と GPU を分けて記録する（どちらが律速かで対策が変わるため）。
 * - cpuMs: main thread で描画値を書き、命令を作って submit するまで
 * - gpuMs: submit から GPU がその仕事を終えたと通知するまで（queue の待ちを含む上限値）
 * - intervalMs: 前のフレームからの rAF の間隔
 * - pixels: 描画寸法の画素数
 */
export type FrameSample = Readonly<{
  cpuMs: number;
  gpuMs?: number;
  intervalMs?: number;
  pixels: number;
}>;

export type Distribution = Readonly<{ p50: number; p95: number; max: number }>;

export type ProbeSummary = Readonly<{
  frames: number;
  cpu?: Distribution;
  gpu?: Distribution;
  interval?: Distribution;
  pixels?: number;
}>;

export type FrameProbe = Readonly<{
  record: (sample: FrameSample) => void;
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

/** 直近 capacity フレームだけを持つ観測。長時間の稼働でも記憶が増え続けない。 */
export const createProbe = (capacity = 600): FrameProbe => {
  const samples: FrameSample[] = [];
  const counts = { skipped: 0 };
  return Object.freeze({
    record: (sample: FrameSample): void => {
      samples.push(sample);
      samples.splice(0, Math.max(0, samples.length - capacity));
    },
    skip: (): void => {
      counts.skipped += 1;
    },
    summary: () => {
      const cpu = distribution(samples.map((sample) => sample.cpuMs));
      const gpu = distribution(defined(samples.map((sample) => sample.gpuMs)));
      const interval = distribution(
        defined(samples.map((sample) => sample.intervalMs)),
      );
      const pixels = samples.at(-1)?.pixels;
      return Object.freeze({
        frames: samples.length,
        skipped: counts.skipped,
        ...(cpu === undefined ? {} : { cpu }),
        ...(gpu === undefined ? {} : { gpu }),
        ...(interval === undefined ? {} : { interval }),
        ...(pixels === undefined ? {} : { pixels }),
      });
    },
  });
};
