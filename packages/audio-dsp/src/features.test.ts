import { describe, expect, it } from "vitest";
import { createFeatureStream, onsetStrength } from "./index";
import type { SpectralFeatures } from "./index";

const spectral = (
  lowEnergy: number,
  midEnergy = 0,
  highEnergy = 0,
): SpectralFeatures => ({
  timeSeconds: 1,
  rms: 0,
  lowEnergy,
  midEnergy,
  highEnergy,
  spectralCentroidHz: 100,
});
const config = {
  sampleRate: 48000,
  fftSize: 1024,
  hopSize: 1024,
  lowMidHz: 250,
  midHighHz: 4000,
};
const block = (start: number, amplitude = 0.5) => ({
  sampleRate: 48000,
  timeSeconds: start / 48000,
  channels: [
    Float32Array.from(
      { length: 1024 },
      (_, index) =>
        amplitude * Math.sin((2 * Math.PI * 16 * (start + index)) / 1024),
    ),
  ],
});

describe("帯域振幅の立ち上がり", () => {
  it("無音は0、増加は正、定常と減少は0", () => {
    expect(onsetStrength(spectral(0), null)).toBe(0);
    expect(onsetStrength(spectral(0.25), spectral(0))).toBeCloseTo(1 / 6, 5);
    expect(onsetStrength(spectral(0.25), spectral(0.25))).toBe(0);
    expect(onsetStrength(spectral(0), spectral(0.25))).toBe(0);
  });
  it("総量が同じでも帯域が移動すれば立ち上がりを検出する", () => {
    expect(onsetStrength(spectral(0, 0.25), spectral(0.25))).toBeGreaterThan(0);
  });
  it("定常sineでは最初だけ立ち上がり、その後は沈静する", () => {
    const first = createFeatureStream(config).push(block(0));
    const second = first.next.push(block(1024));
    expect(first.features[0]?.onset).toBeGreaterThan(0);
    expect(second.features[0]?.onset).toBe(0);
    expect(second.features[0]?.midEnergy).toBeGreaterThan(0.1);
  });
  it("不連続と再作成では前の窓や立ち上がり基準を引き継がない", () => {
    const first = createFeatureStream(config).push(block(0));
    const gap = first.next.push(block(4096));
    const reset = createFeatureStream(config).push(block(4096));
    expect(gap.features).toEqual(reset.features);
    expect(gap.features[0]?.onset).toBeGreaterThan(0);
  });
  it("不正PCMの後も無音から回復し、NaNを通知しない", () => {
    const invalid = createFeatureStream(config).push({
      ...block(0),
      channels: [new Float32Array([NaN])],
    });
    expect(invalid.features).toEqual([]);
    const result = invalid.next.push(block(1024, 0));
    expect(result.features[0]).toEqual({
      timeSeconds: 2048 / 48000,
      rms: 0,
      lowEnergy: 0,
      midEnergy: 0,
      highEnergy: 0,
      onset: 0,
      spectralCentroidHz: 0,
    });
  });
});
