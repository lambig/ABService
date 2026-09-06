import { describe, expect, it } from 'vitest';

import { ReferenceRmsExtractor, referenceRms } from './reference.js';

const samples = (...values: readonly number[]): Float32Array => Float32Array.from(values);

describe('referenceRms', () => {
  it('returns zero for no channels', () => {
    expect(referenceRms([])).toBe(0);
  });

  it('returns zero for silent channels', () => {
    expect(referenceRms([samples(0, 0, 0, 0)])).toBe(0);
  });

  it('calculates mono RMS', () => {
    expect(referenceRms([samples(1, -1, 1, -1)])).toBe(1);
  });

  it('averages channel energy before taking the root', () => {
    expect(referenceRms([samples(1, 1), samples(0, 0)])).toBeCloseTo(Math.SQRT1_2);
  });

  it('clamps malformed input above the normalized range', () => {
    expect(referenceRms([samples(2, -2)])).toBe(1);
  });
});

describe('ReferenceRmsExtractor', () => {
  it('preserves AudioContext time and leaves unavailable features neutral', () => {
    const result = new ReferenceRmsExtractor().extract({
      sampleRateHz: 48_000,
      timeSeconds: 12.5,
      channels: [samples(0.5, -0.5)],
    });

    expect(result).toEqual({
      timeSeconds: 12.5,
      rms: 0.5,
      lowEnergy: 0,
      midEnergy: 0,
      highEnergy: 0,
      onset: 0,
      spectralCentroidHz: 0,
    });
  });
});
