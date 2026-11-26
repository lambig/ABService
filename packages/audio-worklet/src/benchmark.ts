import type { QuantumBenchmark } from "abservice-audio-dsp";
import { loadAudioKernel } from "./kernel-module";
import { prepareRustStream } from "./rust-stream";

/** Measure the production Rust kernel's copy/push/snapshot cost in a Worker, separately from Worklet scheduling. */
export const benchmarkRustAudio = async (
  channels: readonly Float32Array[],
  sampleRate: number,
  maxQuanta: number,
  now: () => number,
): Promise<QuantumBenchmark> => {
  const valid =
    Number.isSafeInteger(maxQuanta) &&
    maxQuanta >= 0 &&
    [1, 2].includes(channels.length) &&
    channels.every((channel) => channel.length === channels[0]?.length);
  const check = (): void => {
    throw new RangeError("Invalid audio benchmark input");
  };
  (valid ? () => undefined : check)();
  const stream = prepareRustStream(
    await loadAudioKernel(new AbortController().signal),
    sampleRate,
  );
  try {
    const quanta = Math.min(
      Math.floor((channels[0]?.length ?? 0) / 128),
      maxQuanta,
    );
    const durations = Array.from({ length: quanta }, (_, index) => {
      const frame = index * 128;
      const block = channels.map((channel) =>
        channel.subarray(frame, frame + 128),
      );
      const begin = now();
      stream.push(block, frame);
      return now() - begin;
    }).sort((left, right) => left - right);
    const percentile = (fraction: number): number =>
      durations[
        Math.min(durations.length - 1, Math.floor(durations.length * fraction))
      ] ?? 0;
    const quantumMs = (128 / sampleRate) * 1000,
      p95Ms = percentile(0.95),
      maxMs = durations.at(-1) ?? 0;
    return Object.freeze({
      quanta,
      quantumFrames: 128,
      quantumMs,
      p50Ms: percentile(0.5),
      p95Ms,
      maxMs,
      p95Ratio: p95Ms / quantumMs,
      maxRatio: maxMs / quantumMs,
    });
  } finally {
    stream.dispose();
  }
};
