import type { AudioFeatures } from "abservice-audio-dsp";

/** 描画だけの値域を持つsnapshot。Rendererは音響特徴量を参照しない。 */
export type PresentationFrame = Readonly<{
  timeSeconds: number;
  sceneScale: number;
  backgroundIntensity: number;
  artworkScale: number;
  artworkOffset: number;
  textOffset: number;
  textOpacity: number;
  effectIntensity: number;
  impulse: number;
  textureScale: number;
}>;

/** 時間ベースの平滑化設定。各応答の上限はmapper内で固定する。 */
export type MappingOptions = Readonly<{
  smoothingSeconds: number;
  impulseDecaySeconds: number;
  centroidCeilingHz: number;
}>;

/** 初期値。秒単位の平滑化で描画fpsに依存する係数を避ける。 */
export const defaultMappingOptions: MappingOptions = Object.freeze({
  smoothingSeconds: 0.12,
  impulseDecaySeconds: 0.16,
  centroidCeilingHz: 12000,
});

const finite = (value: number, fallback = 0): number =>
  Number.isFinite(value) ? value : fallback;
const clamp = (value: number, min = 0, max = 1): number =>
  Math.min(max, Math.max(min, finite(value)));
const positive = (value: number, fallback: number): number =>
  Number.isFinite(value) && value > 0 ? value : fallback;

/** 静止時の描画値。再生開始やseekで前回の応答を引き継がない。 */
export const restingFrame: PresentationFrame = Object.freeze({
  timeSeconds: 0,
  sceneScale: 1,
  backgroundIntensity: 0.15,
  artworkScale: 1,
  artworkOffset: 0,
  textOffset: 0,
  textOpacity: 0.65,
  effectIntensity: 0,
  impulse: 0,
  textureScale: 1,
});

/**
 * AudioFeaturesから描画値を生成する純粋関数。
 * 不正な数値を中立値にし、時間の巻き戻りは静止値へresetする。
 * onsetは正規化強度をピークとして取り込み、無入力時に指数減衰する。
 */
export const mapFeatures = (
  input: AudioFeatures,
  previous: PresentationFrame = restingFrame,
  options: MappingOptions = defaultMappingOptions,
): PresentationFrame => {
  const timeSeconds = Math.max(0, finite(input.timeSeconds));
  const reset = timeSeconds < previous.timeSeconds;
  const base = reset ? restingFrame : previous;
  const dt = reset ? 0 : timeSeconds - previous.timeSeconds;
  const alpha = 1 - Math.exp(-dt / positive(options.smoothingSeconds, 0.12));
  const smooth = (before: number, target: number): number =>
    before + (target - before) * alpha;
  const impulse = Math.max(
    clamp(input.onset),
    base.impulse * Math.exp(-dt / positive(options.impulseDecaySeconds, 0.16)),
  );
  return Object.freeze({
    timeSeconds,
    sceneScale: smooth(base.sceneScale, 1 + 0.035 * clamp(input.rms)),
    backgroundIntensity: smooth(
      base.backgroundIntensity,
      0.15 + 0.4 * clamp(input.rms),
    ),
    artworkScale: smooth(base.artworkScale, 1 + 0.08 * clamp(input.lowEnergy)),
    artworkOffset: smooth(base.artworkOffset, 0.025 * clamp(input.lowEnergy)),
    textOffset: smooth(base.textOffset, 0.025 * clamp(input.midEnergy)),
    textOpacity: smooth(base.textOpacity, 0.65 + 0.35 * clamp(input.midEnergy)),
    effectIntensity: smooth(
      base.effectIntensity,
      0.35 * clamp(input.highEnergy),
    ),
    impulse,
    textureScale: smooth(
      base.textureScale,
      1 +
        clamp(
          input.spectralCentroidHz / positive(options.centroidCeilingHz, 12000),
        ),
    ),
  });
};

/** 同じ時刻から同じ値を作るfake source。DSP解析や音声の再生は行わない。 */
export const fakeFeatures = (seconds: number): AudioFeatures => {
  const timeSeconds = Math.max(0, finite(seconds));
  const wave = (speed: number, offset = 0): number =>
    (1 + Math.sin(timeSeconds * speed + offset)) / 2;
  return Object.freeze({
    timeSeconds,
    rms: wave(1.2),
    lowEnergy: wave(1.6),
    midEnergy: wave(0.9, 1),
    highEnergy: wave(2.1, 2),
    onset: timeSeconds % 2 < 0.05 ? 0.8 : 0,
    spectralCentroidHz: 800 + 9000 * wave(0.35),
  });
};
