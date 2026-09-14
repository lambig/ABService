import { describe, expect, it } from "vitest";
import {
  createSpectralDsp,
  createSpectralStream,
  defaultSpectralConfig,
} from "./index";
import type {
  DspResult,
  PcmBlock,
  SpectralConfig,
  SpectralFeatures,
  SpectralPushResult,
} from "./index";

const signal = (
  length: number,
  sampleRate: number,
  hz: number,
  amplitude = 0.5,
): Float32Array =>
  Float32Array.from(
    { length },
    (_, index) => amplitude * Math.sin((2 * Math.PI * hz * index) / sampleRate),
  );
const pcm = (
  channels: readonly Float32Array[],
  sampleRate = 48000,
  startFrame = 0,
): PcmBlock => ({ channels, sampleRate, timeSeconds: startFrame / sampleRate });
const feature = (result: DspResult<SpectralFeatures>): SpectralFeatures => {
  expect(result.kind).toBe("features");
  return result.kind === "features" ? result.features : fail();
};
const frames = (result: SpectralPushResult): readonly SpectralFeatures[] => {
  expect(result.kind).toBe("frames");
  return result.kind === "frames" ? result.features : fail();
};
const fail = (): never => {
  throw new Error("Expected valid DSP input");
};
const total = (value: SpectralFeatures): number =>
  value.lowEnergy + value.midEnergy + value.highEnergy;

describe("spectral window", () => {
  it.each([
    [93.75, "lowEnergy"],
    [1500, "midEnergy"],
    [6000, "highEnergy"],
  ] as const)(
    "%s Hz is isolated in %s with absolute mean power",
    (hz, band) => {
      const value = feature(
        createSpectralDsp().analyze(pcm([signal(2048, 48000, hz)])),
      );
      expect(value[band]).toBeCloseTo(0.125, 7);
      expect(total(value)).toBeCloseTo(0.125, 7);
      expect(value.rms).toBeCloseTo(Math.SQRT1_2 / 2, 7);
      expect(value.spectralCentroidHz).toBeCloseTo(hz, 5);
      expect(value.timeSeconds).toBe(2048 / 48000);
      expect(value).not.toHaveProperty("onset");
      expect(Object.isFrozen(value)).toBe(true);
    },
  );

  it("keeps silence finite and exactly zero", () => {
    expect(
      feature(createSpectralDsp().analyze(pcm([new Float32Array(2048)]))),
    ).toEqual({
      timeSeconds: 2048 / 48000,
      rms: 0,
      lowEnergy: 0,
      midEnergy: 0,
      highEnergy: 0,
      spectralCentroidHz: 0,
    });
  });

  it("uses power, not magnitude or relative band share, to weight mixed tones", () => {
    const mixed = Float32Array.from(
      { length: 2048 },
      (_, index) =>
        0.5 * Math.sin((2 * Math.PI * 1500 * index) / 48000) +
        0.25 * Math.sin((2 * Math.PI * 6000 * index) / 48000),
    );
    const value = feature(createSpectralDsp().analyze(pcm([mixed])));
    expect(value.midEnergy).toBeCloseTo(0.125, 7);
    expect(value.highEnergy).toBeCloseTo(0.03125, 7);
    expect(value.spectralCentroidHz).toBeCloseTo(2400, 4);
  });

  it("averages channel power without phase cancellation or mono duplication", () => {
    const left = signal(2048, 48000, 1500);
    const opposite = Float32Array.from(left, (value) => -value);
    const dsp = createSpectralDsp();
    const mono = feature(dsp.analyze(pcm([left])));
    expect(feature(dsp.analyze(pcm([left, opposite])))).toEqual(mono);
    const oneSide = feature(dsp.analyze(pcm([left, new Float32Array(2048)])));
    expect(oneSide.midEnergy).toBeCloseTo(mono.midEnergy / 2, 12);
    expect(oneSide.rms).toBeCloseTo(mono.rms / Math.sqrt(2), 12);
    expect(oneSide.spectralCentroidHz).toBe(mono.spectralCentroidHz);
  });

  it.each([44100, 48000, 96000])(
    "uses the configured %i Hz sample rate",
    (sampleRate) => {
      const hz = (64 * sampleRate) / 2048;
      const value = feature(
        createSpectralDsp({ ...defaultSpectralConfig, sampleRate }).analyze(
          pcm([signal(2048, sampleRate, hz)], sampleRate),
        ),
      );
      expect(value.midEnergy).toBeCloseTo(0.125, 7);
      expect(value.spectralCentroidHz).toBeCloseTo(hz, 5);
    },
  );

  it.each([64, 256, 8192])(
    "preserves normalization at FFT size %i",
    (fftSize) => {
      const config = {
        ...defaultSpectralConfig,
        fftSize,
        hopSize: fftSize / 4,
      };
      const value = feature(
        createSpectralDsp(config).analyze(pcm([signal(fftSize, 48000, 6000)])),
      );
      expect(total(value)).toBeCloseTo(0.125, 7);
      expect(value.spectralCentroidHz).toBeCloseTo(6000, 5);
    },
  );

  it("measures a centered impulse with window-power correction, not unwindowed RMS power", () => {
    const value = feature(
      createSpectralDsp().analyze(
        pcm([
          Float32Array.from({ length: 2048 }, (_, index) =>
            index === 1024 ? 1 : 0,
          ),
        ]),
      ),
    );
    expect(total(value)).toBeCloseTo(8 / (3 * 2048), 12);
    expect(value.rms ** 2).toBeCloseTo(1 / 2048, 12);
    expect(value.spectralCentroidHz).toBeCloseTo(12000, 8);
  });

  it("copies the caller's configuration at initialization", () => {
    const config = { ...defaultSpectralConfig };
    const dsp = createSpectralDsp(config);
    /* eslint-disable-next-line functional/immutable-data -- 呼び出し元の変更からDSP設定が独立することを検証する。 */
    config.fftSize = 64;
    expect(feature(dsp.analyze(pcm([new Float32Array(2048)]))).rms).toBe(0);
  });

  it("counts DC and Nyquist once, preserving Hann power correction", () => {
    const dsp = createSpectralDsp();
    const dc = feature(
      dsp.analyze(pcm([Float32Array.from({ length: 2048 }, () => 0.5)])),
    );
    const nyquist = feature(
      dsp.analyze(
        pcm([
          Float32Array.from({ length: 2048 }, (_, index) =>
            index % 2 === 0 ? 0.5 : -0.5,
          ),
        ]),
      ),
    );
    expect(dc.lowEnergy).toBeCloseTo(0.25, 12);
    expect(nyquist.highEnergy).toBeCloseTo(0.25, 12);
    expect(dc.spectralCentroidHz).toBeCloseTo(48000 / 2048 / 3, 8);
    expect(nyquist.spectralCentroidHz).toBeCloseTo(24000 - 48000 / 2048 / 3, 8);
  });

  it("assigns a boundary bin to the upper band, including Hann leakage into its neighbors", () => {
    const dsp = createSpectralDsp({ ...defaultSpectralConfig, lowMidHz: 375 });
    const value = feature(dsp.analyze(pcm([signal(2048, 48000, 375)])));
    expect(value.lowEnergy).toBeCloseTo(0.125 / 6, 7);
    expect(value.midEnergy).toBeCloseTo((0.125 * 5) / 6, 7);
  });

  it("agrees with a direct DFT oracle on nonperiodic clipped PCM", () => {
    const size = 64;
    const config = {
      ...defaultSpectralConfig,
      fftSize: size,
      hopSize: 16,
      sampleRate: 8192,
      lowMidHz: 1024,
      midHighHz: 2048,
    };
    const samples = Float32Array.from(
      { length: size },
      (_, index) => Math.sin(index * 1.7) * 1.8 + index / 100,
    );
    const windowed = Array.from(
      samples,
      (value, index) =>
        Math.max(-1, Math.min(1, value)) *
        (0.5 - 0.5 * Math.cos((2 * Math.PI * index) / size)),
    );
    const bins = Array.from({ length: size / 2 + 1 }, (_, k) => {
      const real = windowed.reduce(
        (sum, value, n) => sum + value * Math.cos((2 * Math.PI * k * n) / size),
        0,
      );
      const imaginary = windowed.reduce(
        (sum, value, n) => sum - value * Math.sin((2 * Math.PI * k * n) / size),
        0,
      );
      return {
        hz: k * 128,
        power:
          ((real ** 2 + imaginary ** 2) *
            (k === 0 ? 1 : k === size / 2 ? 1 : 2)) /
          ((size ** 2 * 3) / 8),
      };
    });
    const value = feature(
      createSpectralDsp(config).analyze(pcm([samples], 8192)),
    );
    expect(value.lowEnergy).toBeCloseTo(
      bins
        .filter((bin) => bin.hz < 1024)
        .reduce((sum, bin) => sum + bin.power, 0),
      12,
    );
    expect(value.midEnergy).toBeCloseTo(
      bins
        .filter((bin) => bin.hz >= 1024 && bin.hz < 2048)
        .reduce((sum, bin) => sum + bin.power, 0),
      12,
    );
    expect(value.highEnergy).toBeCloseTo(
      bins
        .filter((bin) => bin.hz >= 2048)
        .reduce((sum, bin) => sum + bin.power, 0),
      12,
    );
    expect(value.spectralCentroidHz).toBeCloseTo(
      bins.reduce((sum, bin) => sum + bin.hz * bin.power, 0) /
        bins.reduce((sum, bin) => sum + bin.power, 0),
      8,
    );
    expect(total(value)).toBeCloseTo(
      windowed.reduce((sum, x) => sum + x ** 2, 0) / ((size * 3) / 8),
      12,
    );
  });

  it.each([0, 32, 63, 65, 16384, Infinity, NaN])(
    "rejects FFT size %s before allocating",
    (fftSize) => {
      expect(() =>
        createSpectralDsp({ ...defaultSpectralConfig, fftSize }),
      ).toThrow(RangeError);
    },
  );
  it.each([
    { hopSize: 0 },
    { hopSize: 2049 },
    { hopSize: 1.5 },
    { sampleRate: 0 },
    { sampleRate: Infinity },
    { sampleRate: Number.MIN_VALUE },
    { sampleRate: Number.MAX_VALUE },
    { lowMidHz: 0 },
    { lowMidHz: 4000 },
    { midHighHz: 24000 },
  ])("rejects invalid config %o", (invalid) => {
    expect(() =>
      createSpectralStream({ ...defaultSpectralConfig, ...invalid }),
    ).toThrow(RangeError);
  });
  it.each([
    pcm([]),
    pcm([new Float32Array(63)]),
    pcm([new Float32Array(2048), new Float32Array(1)]),
    pcm([new Float32Array(2048)], 44100),
    pcm([Float32Array.from({ length: 2048 }, () => NaN)]),
    pcm([Float32Array.from({ length: 2048 }, () => Infinity)]),
    pcm([new Float32Array(2048)], 48000, Number.MAX_SAFE_INTEGER),
  ])("rejects invalid windows without emitting a feature", (block) => {
    expect(createSpectralDsp().analyze(block)).toEqual({
      kind: "invalid-input",
    });
  });
});

describe("spectral hop", () => {
  const config: SpectralConfig = {
    ...defaultSpectralConfig,
    fftSize: 64,
    hopSize: 16,
  };
  it("produces identical overlapping windows regardless of input partition", () => {
    const samples = signal(160, 48000, 6000);
    const all = frames(createSpectralStream(config).push(pcm([samples])));
    const split = [7, 24, 63, 65, 129, 160].reduce(
      (state, end) => {
        const result = state.stream.push(
          pcm([samples.slice(state.end, end)], 48000, state.end),
        );
        return {
          stream: result.next,
          end,
          features: [...state.features, ...frames(result)],
        };
      },
      {
        stream: createSpectralStream(config),
        end: 0,
        features: [] as readonly SpectralFeatures[],
      },
    );
    expect(split.features).toEqual(all);
    expect(all.map((value) => value.timeSeconds)).toEqual(
      [64, 80, 96, 112, 128, 144, 160].map((frame) => frame / 48000),
    );
    expect(Object.isFrozen(all)).toBe(true);
  });

  it("copies borrowed PCM and leaves earlier stream states reusable", () => {
    const initial = createSpectralStream(config);
    const borrowed = Float32Array.from({ length: 32 }, () => 0.5);
    const first = initial.push(pcm([borrowed]));
    borrowed.fill(0);
    expect(frames(first)).toEqual([]);
    const rest = pcm([Float32Array.from({ length: 32 }, () => 0.5)], 48000, 32);
    expect(frames(first.next.push(rest))[0]?.rms).toBe(0.5);
    expect(frames(initial.push(rest))).toEqual([]);
    expect(frames(first.next.push(rest))[0]?.rms).toBe(0.5);
  });

  it.each([0, 64])(
    "discards partial windows on seek/gap to frame %i",
    (start) => {
      const partial = createSpectralStream(config).push(
        pcm([Float32Array.from({ length: 32 }, () => 1)]),
      );
      const next = partial.next.push(pcm([new Float32Array(64)], 48000, start));
      expect(frames(next)[0]?.rms).toBe(0);
      expect(frames(next)[0]?.timeSeconds).toBe((start + 64) / 48000);
    },
  );

  it("resets on invalid input and on channel count change", () => {
    const partial = createSpectralStream(config).push(
      pcm([Float32Array.from({ length: 32 }, () => 1)]),
    );
    const invalid = partial.next.push(pcm([]));
    expect(invalid.kind).toBe("invalid-input");
    expect(
      frames(invalid.next.push(pcm([new Float32Array(32)], 48000, 32))),
    ).toEqual([]);
    const stereo = partial.next.push(
      pcm([new Float32Array(64), new Float32Array(64)], 48000, 32),
    );
    expect(frames(stereo)[0]?.rms).toBe(0);
  });
});
