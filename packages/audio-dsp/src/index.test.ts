import { describe, expect, it } from "vitest";
import { audioFeatures, rmsDsp } from "./index";
import type { AudioFeatures, DspPort, PcmBlock, RmsFeatures } from "./index";

const block = (channels: readonly Float32Array[]): PcmBlock => ({
  channels,
  sampleRate: 48000,
  timeSeconds: 2,
});
const sine = Float32Array.from({ length: 480 }, (_, index) =>
  Math.sin((2 * Math.PI * index) / 48),
);
const zero: AudioFeatures = {
  timeSeconds: 0,
  rms: 0,
  lowEnergy: 0,
  midEnergy: 0,
  highEnergy: 0,
  onset: 0,
  spectralCentroidHz: 0,
};

/** 型検査を DOM 無しで通すこと自体が描画側の独立性を検証する。 */
const presentation = (features: RmsFeatures): number => features.rms * 100;

describe("RMS reference DSP", () => {
  it("無音を時刻付きゼロとして渡す", () => {
    expect(rmsDsp.analyze(block([new Float32Array(128)]))).toEqual({
      kind: "features",
      features: { timeSeconds: 2, rms: 0 },
    });
  });
  it("定常 sine の RMS は 1/sqrt(2)", () => {
    const result = rmsDsp.analyze(block([sine]));
    expect(result.kind).toBe("features");
    expect(result.kind === "features" ? result.features.rms : NaN).toBeCloseTo(
      Math.SQRT1_2,
      6,
    );
  });
  it("左右逆相の音量を消さない", () => {
    const result = rmsDsp.analyze(block([sine, sine.map((value) => -value)]));
    expect(result.kind === "features" ? result.features.rms : NaN).toBeCloseTo(
      Math.SQRT1_2,
      6,
    );
  });
  it("片チャンネルのみの信号は平均パワーへ畳む", () => {
    const result = rmsDsp.analyze(block([sine, new Float32Array(480)]));
    expect(result.kind === "features" ? result.features.rms : NaN).toBeCloseTo(
      0.5,
      6,
    );
  });
  it("impulse のパワーをフレーム数で平均する", () => {
    expect(rmsDsp.analyze(block([Float32Array.of(1, 0, 0, 0)]))).toEqual({
      kind: "features",
      features: { timeSeconds: 2, rms: 0.5 },
    });
  });
  it("full scale を超える有限値はサンプル単位で制限する", () => {
    expect(rmsDsp.analyze(block([Float32Array.of(2, -2)]))).toEqual({
      kind: "features",
      features: { timeSeconds: 2, rms: 1 },
    });
  });
  it.each([
    [],
    [new Float32Array(0)],
    [sine, new Float32Array(1)],
    [sine, sine, sine],
    [Float32Array.of(NaN)],
    [Float32Array.of(Infinity)],
    [Float32Array.of(-Infinity)],
  ])("不正なチャンネル構成・サンプルを拒否する: %j", (...channels) => {
    expect(rmsDsp.analyze(block(channels))).toEqual({ kind: "invalid-input" });
  });
  it.each([0, -1, NaN, Infinity])(
    "不正な sample rate を拒否する: %s",
    (sampleRate) => {
      expect(rmsDsp.analyze({ ...block([sine]), sampleRate })).toEqual({
        kind: "invalid-input",
      });
    },
  );
  it.each([-1, NaN, Infinity])("不正な時刻を拒否する: %s", (timeSeconds) => {
    expect(rmsDsp.analyze({ ...block([sine]), timeSeconds })).toEqual({
      kind: "invalid-input",
    });
  });
  it("入力を変更せず、PCM の再利用後も snapshot が変わらない", () => {
    const pcm = Float32Array.of(1, -1);
    const result = rmsDsp.analyze(block([pcm]));
    expect(pcm).toEqual(Float32Array.of(1, -1));
    // BUFFER-REUSE: 借用元による次ブロックの上書きを再現し、snapshot の独立性を検証する
    pcm.fill(0);
    expect(result.kind === "features" && Object.isFrozen(result.features)).toBe(
      true,
    );
    expect(rmsDsp.analyze(block([new Float32Array(2)]))).toMatchObject({
      features: { rms: 0 },
    });
    expect(result).toMatchObject({ features: { rms: 1 } });
  });
  it("DSP の差し替えと描画は Web Audio 型を要求しない", () => {
    const replacement: DspPort<RmsFeatures> = {
      analyze: () => ({
        kind: "features",
        features: { timeSeconds: 2, rms: 0.25 },
      }),
    };
    const result = replacement.analyze(block([sine]));
    expect(
      result.kind === "features" ? presentation(result.features) : NaN,
    ).toBe(25);
  });
});

describe("AudioFeatures v0", () => {
  it("値域の両端と Hz の値を許容し凍結する", () => {
    const value = {
      ...zero,
      rms: 1,
      lowEnergy: 1,
      midEnergy: 1,
      highEnergy: 1,
      onset: 1,
      spectralCentroidHz: 24000,
    };
    const result = audioFeatures(value);
    expect(result).toEqual({ kind: "features", features: value });
    expect(result.kind === "features" && Object.isFrozen(result.features)).toBe(
      true,
    );
    expect(result.kind === "features" && result.features === value).toBe(false);
    expect(audioFeatures(zero)).toEqual({ kind: "features", features: zero });
  });
  it.each(["rms", "lowEnergy", "midEnergy", "highEnergy", "onset"] as const)(
    "正規化値域を検証する: %s",
    (field) => {
      [-1, 1.01, NaN, Infinity].forEach((value) => {
        expect(audioFeatures({ ...zero, [field]: value })).toEqual({
          kind: "invalid-input",
        });
      });
    },
  );
  it.each(["timeSeconds", "spectralCentroidHz"] as const)(
    "非負有限値を検証する: %s",
    (field) => {
      [-1, NaN, Infinity].forEach((value) => {
        expect(audioFeatures({ ...zero, [field]: value })).toEqual({
          kind: "invalid-input",
        });
      });
    },
  );
});
