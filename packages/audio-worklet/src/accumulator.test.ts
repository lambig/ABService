import { describe, expect, it } from "vitest";
import { collectRms, emptyWindow, intervalFrames } from "./accumulator";
import type { PcmBlock } from "abservice-audio-dsp";

const block = (start: number, frames: number, level: number): PcmBlock => ({
  channels: [Float32Array.from({ length: frames }, () => level)],
  sampleRate: 48000,
  timeSeconds: start / 48000,
});

describe("notification window", () => {
  it("weights all samples, including an impulse before the notification block", () => {
    const first = collectRms(block(0, 1, 1), emptyWindow, 4);
    expect(first.features).toBeNull();
    const second = collectRms(block(1, 3, 0), first.window, 4);
    expect(second.features).toEqual({ timeSeconds: 4 / 48000, rms: 0.5 });
    expect(Object.isFrozen(second.features)).toBe(true);
    expect(second.window.power).toBe(0);
    expect(second.window.frames).toBe(0);
  });

  it.each([10, 30, 60])(
    "retains phase remainder at %i Hz across 128-frame blocks",
    (rate) => {
      const interval = intervalFrames(48000, rate);
      const result = Array.from({ length: 375 }, (_, index) => index).reduce(
        (state, index) => {
          const next = collectRms(
            block(index * 128, 128, 0.25),
            state.window,
            interval,
          );
          return {
            window: next.window,
            features:
              next.features === null
                ? state.features
                : [...state.features, next.features],
          };
        },
        {
          window: emptyWindow,
          features: [] as Readonly<{ timeSeconds: number; rms: number }>[],
        },
      );
      expect(result.features).toHaveLength(rate);
      result.features.forEach((feature, index) => {
        expect(feature.rms).toBe(0.25);
        expect(feature.timeSeconds).toBeGreaterThanOrEqual((index + 1) / rate);
        expect(feature.timeSeconds).toBeLessThan(
          (index + 1) / rate + 128 / 48000,
        );
      });
    },
  );

  it("discards unfinished energy after missing or invalid input", () => {
    const partial = collectRms(block(0, 128, 1), emptyWindow, 256);
    const gap = collectRms(block(256, 256, 0), partial.window, 256);
    expect(gap.features?.rms).toBe(0);
    const invalid = collectRms(
      { ...block(128, 128, 1), channels: [] },
      partial.window,
      256,
    );
    expect(invalid).toEqual({
      valid: false,
      window: emptyWindow,
      features: null,
    });
    const next = collectRms(block(256, 256, 0), invalid.window, 256);
    expect(next.features?.rms).toBe(0);
  });

  it.each([0, -1, 61, NaN, Infinity])(
    "rejects invalid frequency %s",
    (rate) => {
      expect(() => intervalFrames(48000, rate)).toThrow(RangeError);
    },
  );
});
