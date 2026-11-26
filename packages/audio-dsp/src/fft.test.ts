import { describe, expect, it } from "vitest";
import { createSquaredSpectrum, squaredSpectrum } from "./fft";

// ORACLE: A direct DFT avoids reproducing the FFT butterfly implementation in the expected result.
const reference = (samples: readonly number[]): readonly number[] =>
  Array.from({ length: samples.length / 2 + 1 }, (_, bin) => {
    const sum = samples.reduce(
      (value, sample, index) => {
        const angle = (-2 * Math.PI * bin * index) / samples.length;
        return {
          real: value.real + sample * Math.cos(angle),
          imaginary: value.imaginary + sample * Math.sin(angle),
        };
      },
      { real: 0, imaginary: 0 },
    );
    return sum.real ** 2 + sum.imaginary ** 2;
  });

describe("one-sided FFT power", () => {
  it("reuses scratch across unrelated signals without changing retained output or input", () => {
    const transform = createSquaredSpectrum(64);
    const first = Object.freeze(
      Array.from({ length: 64 }, (_, i) => Math.sin(i * 0.173)),
    );
    const output = transform(first);
    const retained = [...output];
    const samples = Object.freeze(
      Array.from({ length: 64 }, (_, i) => Math.cos(i * 0.217)),
    );
    const second = transform(samples);
    const expected = reference(samples);
    second.forEach((value, i) => {
      expect(value).toBeCloseTo(expected[i] as number, 8);
    });
    expect(transform(new Float64Array(64))).toEqual(
      Array.from({ length: 33 }, () => 0),
    );
    expect(output).toEqual(retained);
    expect(transform(first)).toEqual(output);
  });
  it.each([64, 128, 256])(
    "matches a DFT for non-bin-aligned input of size %s",
    (size) => {
      const input = Object.freeze(
        Array.from(
          { length: size },
          (_, i) =>
            0.3 * Math.sin(i * 0.173) +
            0.2 * Math.cos(i * 0.071) +
            (i % 7) / 19,
        ),
      );
      const actual = squaredSpectrum(input);
      const expected = reference(input);
      expect(actual).toHaveLength(size / 2 + 1);
      actual.forEach((value, i) => {
        expect(value).toBeCloseTo(expected[i] as number, 7);
      });
    },
  );

  it.each([64, 128, 256, 512, 1024, 2048, 4096, 8192])(
    "preserves DC, Nyquist, impulse and Parseval power at size %s",
    (size) => {
      const dc = squaredSpectrum(Array.from({ length: size }, () => 0.5));
      expect(dc[0]).toBeCloseTo((size * size) / 4, 7);
      expect(dc.slice(1).every((x) => Math.abs(x) < 1e-12)).toBe(true);
      const nyquist = squaredSpectrum(
        Array.from({ length: size }, (_, i) => (i % 2 ? -0.5 : 0.5)),
      );
      expect(nyquist[size / 2]).toBeCloseTo((size * size) / 4, 7);
      expect(nyquist.slice(0, -1).every((x) => Math.abs(x) < 1e-12)).toBe(true);
      const impulse = squaredSpectrum(
        Array.from({ length: size }, (_, i) => (i === 3 ? 1 : 0)),
      );
      impulse.forEach((x) => {
        expect(x).toBeCloseTo(1, 10);
      });
      const samples = Array.from(
        { length: size },
        (_, i) => Math.sin(i * 0.117) / 2,
      );
      const powers = squaredSpectrum(samples);
      const frequencyPower =
        powers.reduce(
          (sum, x, i) => sum + x * (i === 0 ? 1 : i === size / 2 ? 1 : 2),
          0,
        ) / size;
      expect(frequencyPower).toBeCloseTo(
        samples.reduce((sum, x) => sum + x * x, 0),
        8,
      );
      const retained = [...powers];
      squaredSpectrum(Array.from({ length: size }, () => 0));
      expect(powers).toEqual(retained);
    },
  );
});
