import { describe, expect, it } from "vitest";
import { fakeFeatures, mapFeatures, restingFrame } from "./index";
import type { AudioFeatures } from "abservice-audio-dsp";

const silence: AudioFeatures = {
  timeSeconds: 1,
  rms: 0,
  lowEnergy: 0,
  midEnergy: 0,
  highEnergy: 0,
  onset: 0,
  spectralCentroidHz: 0,
};
describe("feature mapping", () => {
  it("keeps silence at the resting presentation", () => {
    expect(mapFeatures(silence)).toEqual({ ...restingFrame, timeSeconds: 1 });
  });
  it("maps each band only to its intended visual controls", () => {
    const normal = mapFeatures(silence);
    const low = mapFeatures({ ...silence, lowEnergy: 1 });
    expect({
      ...low,
      artworkScale: normal.artworkScale,
      artworkOffset: normal.artworkOffset,
    }).toEqual(normal);
    expect(
      mapFeatures({ ...silence, lowEnergy: 1 }).artworkScale,
    ).toBeGreaterThan(1);
    expect(
      mapFeatures({ ...silence, midEnergy: 1 }).textOpacity,
    ).toBeGreaterThan(normal.textOpacity);
    expect(
      mapFeatures({ ...silence, highEnergy: 1 }).effectIntensity,
    ).toBeGreaterThan(0);
    expect(mapFeatures({ ...silence, rms: 1 }).sceneScale).toBeGreaterThan(1);
    expect(
      mapFeatures({ ...silence, spectralCentroidHz: 12000 }).textureScale,
    ).toBeGreaterThan(1);
  });
  it.each([Number.NaN, Infinity, -Infinity])(
    "keeps invalid numeric input finite (%s)",
    (value) => {
      const frame = mapFeatures({
        timeSeconds: value,
        rms: value,
        lowEnergy: value,
        midEnergy: value,
        highEnergy: value,
        onset: value,
        spectralCentroidHz: value,
      });
      expect(Object.values(frame).every(Number.isFinite)).toBe(true);
      expect(frame).toEqual(restingFrame);
    },
  );
  it("clamps untrusted feature magnitudes", () => {
    const frame = mapFeatures({
      timeSeconds: 100,
      rms: 100,
      lowEnergy: 100,
      midEnergy: 100,
      highEnergy: 100,
      onset: 100,
      spectralCentroidHz: 1e12,
    });
    expect(frame.sceneScale).toBeLessThanOrEqual(1.035);
    expect(frame.artworkScale).toBeLessThanOrEqual(1.08);
    expect(frame.textOffset).toBeLessThanOrEqual(0.025);
    expect(frame.textOpacity).toBeLessThanOrEqual(1);
    expect(frame.effectIntensity).toBeLessThanOrEqual(0.35);
    expect(frame.impulse).toBe(1);
    expect(frame.textureScale).toBeLessThanOrEqual(2);
  });
  it("uses time-based smoothing independently of frame subdivision", () => {
    const full = mapFeatures({ ...silence, rms: 1, timeSeconds: 1 });
    const half = mapFeatures({ ...silence, rms: 1, timeSeconds: 0.5 });
    const subdivided = mapFeatures(
      { ...silence, rms: 1, timeSeconds: 1 },
      half,
    );
    expect(subdivided.sceneScale).toBeCloseTo(full.sceneScale, 12);
    expect(subdivided.backgroundIntensity).toBeCloseTo(
      full.backgroundIntensity,
      12,
    );
  });
  it("takes an onset peak then decays by elapsed time", () => {
    const peak = mapFeatures({ ...silence, onset: 1 });
    expect(peak.impulse).toBe(1);
    expect(
      mapFeatures({ ...silence, timeSeconds: 1.16 }, peak).impulse,
    ).toBeCloseTo(Math.exp(-1));
  });
  it("resets smoothing when time moves backwards", () => {
    const active = mapFeatures({ ...silence, rms: 1, onset: 1 });
    expect(mapFeatures({ ...silence, timeSeconds: 0.5 }, active)).toEqual({
      ...restingFrame,
      timeSeconds: 0.5,
    });
  });
  it("does not advance smoothing for duplicate timestamps", () => {
    const previous = mapFeatures(silence);
    expect(mapFeatures({ ...silence, rms: 1 }, previous)).toEqual(previous);
  });
  it("falls back from invalid smoothing options", () => {
    expect(
      mapFeatures(silence, restingFrame, {
        smoothingSeconds: 0,
        impulseDecaySeconds: Number.NaN,
        centroidCeilingHz: -1,
      }),
    ).toEqual(mapFeatures(silence));
  });
  it("returns frozen snapshots without changing inputs", () => {
    const source = Object.freeze({ ...silence, rms: 0.7 });
    const frame = mapFeatures(source);
    expect(Object.isFrozen(frame)).toBe(true);
    expect(source.rms).toBe(0.7);
    expect(restingFrame.sceneScale).toBe(1);
  });
});
describe("fake source", () => {
  it("is reproducible and stays within the AudioFeatures contract", () => {
    Array.from({ length: 1000 }, (_, index) => index / 60).forEach((time) => {
      const feature = fakeFeatures(time);
      expect(feature).toEqual(fakeFeatures(time));
      expect(
        [
          feature.rms,
          feature.lowEnergy,
          feature.midEnergy,
          feature.highEnergy,
          feature.onset,
        ].every((value) => value >= 0 && value <= 1),
      ).toBe(true);
      expect(feature.spectralCentroidHz).toBeGreaterThanOrEqual(0);
    });
  });
});
