import { describe, expect, it } from "vitest";
import { benchmarkFeatureStream } from "./index";

const sine = (frames: number, sampleRate: number): Float32Array =>
  Float32Array.from({ length: frames }, (_, index) =>
    Math.sin((2 * Math.PI * 440 * index) / sampleRate),
  );
/* A clock read twice per quantum whose readings are precomputed, so each quantum takes exactly the given time. */
const steppingClock = (stepMs: readonly number[], quanta = 64) => {
  const ends = Array.from({ length: quanta }, (_, index) =>
    Array.from(
      { length: index + 1 },
      (__, step) => stepMs[step % stepMs.length] ?? 0,
    ).reduce((sum, value) => sum + value, 0),
  );
  const readings = ends
    .flatMap((end, index) => [ends[index - 1] ?? 0, end])
    .values();
  return () => readings.next().value ?? 0;
};

describe("benchmarkFeatureStream", () => {
  it("times each quantum and relates it to the quantum length", () => {
    const sampleRate = 48000;
    const result = benchmarkFeatureStream([sine(128 * 20, sampleRate)], {
      sampleRate,
      now: steppingClock([0.1, 0.2, 0.3, 0.4, 2]),
    });

    expect(result.quanta).toBe(20);
    expect(result.quantumFrames).toBe(128);
    expect(result.quantumMs).toBeCloseTo(2.6667, 3);
    expect(result.p50Ms).toBeCloseTo(0.3);
    expect(result.p95Ms).toBeCloseTo(2);
    expect(result.maxMs).toBeCloseTo(2);
    expect(result.maxRatio).toBeCloseTo(0.75, 2);
  });

  it("stops at the quantum limit and ignores a partial last quantum", () => {
    const sampleRate = 44100;
    const limited = benchmarkFeatureStream([sine(128 * 10 + 50, sampleRate)], {
      sampleRate,
      now: steppingClock([0.1]),
      maxQuanta: 4,
    });
    const partial = benchmarkFeatureStream([sine(128 * 3 + 50, sampleRate)], {
      sampleRate,
      now: steppingClock([0.1]),
    });

    expect(limited.quanta).toBe(4);
    expect(partial.quanta).toBe(3);
  });

  it("measures the real DSP on stereo input without failing", () => {
    const sampleRate = 48000;
    const left = sine(128 * 64, sampleRate);
    const result = benchmarkFeatureStream([left, left.map((value) => -value)], {
      sampleRate,
      now: () => performance.now(),
    });

    expect(result.quanta).toBe(64);
    expect(Number.isFinite(result.p95Ms)).toBe(true);
    expect(result.p95Ms).toBeGreaterThanOrEqual(0);
  });

  it("reports nothing measured for input shorter than a quantum", () => {
    expect(
      benchmarkFeatureStream([new Float32Array(10)], {
        sampleRate: 48000,
        now: () => 0,
      }),
    ).toMatchObject({ quanta: 0, p50Ms: 0, p95Ms: 0, maxMs: 0, maxRatio: 0 });
  });
});
