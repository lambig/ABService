import { rmsDsp } from "./index";
import type { DspPort, PcmBlock, SpectralFeatures } from "./index";
import { validBlock } from "./pcm";
import { squaredSpectrum } from "./fft";

/** 1..384000 Hz、fftSizeは64..8192の2冪、hopは1..fftSize。窓はperiodic Hann固定。
 * 帯域は[0,lowMid)、[lowMid,midHigh)、[midHigh,Nyquist]。 */
export type SpectralConfig = Readonly<{
  sampleRate: number;
  fftSize: number;
  hopSize: number;
  lowMidHz: number;
  midHighHz: number;
}>;

/** 既定の観測解像度。通知周期とは独立し、将来の端末測定で比較できる設定にする。 */
export const defaultSpectralConfig: SpectralConfig = Object.freeze({
  sampleRate: 48000,
  fftSize: 2048,
  hopSize: 512,
  lowMidHz: 250,
  midHighHz: 4000,
});

const invalidConfig = (): never => {
  throw new RangeError("Invalid spectral configuration");
};
const configuration = (input: SpectralConfig): SpectralConfig =>
  Number.isFinite(input.sampleRate) &&
  input.sampleRate >= 1 &&
  input.sampleRate <= 384000 &&
  Number.isInteger(Math.log2(input.fftSize)) &&
  input.fftSize >= 64 &&
  input.fftSize <= 8192 &&
  Number.isInteger(input.hopSize) &&
  input.hopSize >= 1 &&
  input.hopSize <= input.fftSize &&
  Number.isFinite(input.lowMidHz) &&
  Number.isFinite(input.midHighHz) &&
  input.lowMidHz > 0 &&
  input.lowMidHz < input.midHighHz &&
  input.midHighHz < input.sampleRate / 2
    ? Object.freeze({ ...input })
    : invalidConfig();

const unit = (value: number): number => Math.min(1, Math.max(0, value));
const frameOf = (block: PcmBlock): number =>
  Math.round(block.timeSeconds * block.sampleRate);
const validTiming = (block: PcmBlock): boolean =>
  Number.isSafeInteger(frameOf(block)) &&
  Number.isSafeInteger(frameOf(block) + (block.channels[0]?.length ?? 0));

const windowDsp = (config: SpectralConfig): DspPort<SpectralFeatures> => {
  const weights = Array.from(
    { length: config.fftSize },
    (_, index) => 0.5 - 0.5 * Math.cos((2 * Math.PI * index) / config.fftSize),
  );
  const scale =
    config.fftSize * weights.reduce((sum, value) => sum + value ** 2, 0);
  const analyze = (block: PcmBlock) => {
    const rms = rmsDsp.analyze(block);
    const extract = (level: number) => {
      const spectra = block.channels.map((channel) =>
        squaredSpectrum(
          weights.map(
            (weight, index) =>
              // INVARIANT: analyzeが全チャンネルの窓長を検証してから呼ぶため、重みと同じ添字が存在する。
              weight * Math.max(-1, Math.min(1, channel[index] as number)),
          ),
        ),
      );
      const bins = weights.slice(0, config.fftSize / 2 + 1).map((_, index) => ({
        hz: (index * config.sampleRate) / config.fftSize,
        power:
          (spectra.reduce((sum, spectrum) => sum + (spectrum[index] ?? 0), 0) /
            block.channels.length /
            scale) *
          (index === 0 ? 1 : index === config.fftSize / 2 ? 1 : 2),
      }));
      const total = bins.reduce((sum, bin) => sum + bin.power, 0);
      const energy = (min: number, max: number): number =>
        unit(
          bins
            .filter((bin) => bin.hz >= min && bin.hz < max)
            .reduce((sum, bin) => sum + bin.power, 0),
        );
      return Object.freeze({
        kind: "features" as const,
        features: Object.freeze({
          timeSeconds: (frameOf(block) + config.fftSize) / config.sampleRate,
          rms: level,
          lowEnergy: energy(0, config.lowMidHz),
          midEnergy: energy(config.lowMidHz, config.midHighHz),
          highEnergy: energy(config.midHighHz, Infinity),
          spectralCentroidHz:
            total === 0
              ? 0
              : Math.min(
                  config.sampleRate / 2,
                  bins.reduce((sum, bin) => sum + bin.hz * bin.power, 0) /
                    total,
                ),
        }),
      });
    };
    return rms.kind === "features" &&
      block.sampleRate === config.sampleRate &&
      validTiming(block) &&
      block.channels[0]?.length === config.fftSize
      ? extract(rms.features.rms)
      : Object.freeze({ kind: "invalid-input" as const });
  };
  return Object.freeze({ analyze });
};

/** 正確にfftSizeフレームの窓を解析する同期DSP。時刻は窓末尾、RMSは窓を掛ける前のPCM。 */
export const createSpectralDsp = (
  config: SpectralConfig = defaultSpectralConfig,
): DspPort<SpectralFeatures> => windowDsp(configuration(config));

/** nextを採用して進めるimmutable stream。入力PCMの参照を保持しない。 */
export type SpectralStream = Readonly<{
  push: (block: PcmBlock) => SpectralPushResult;
}>;
/** 部分窓ではfeaturesが空。不正入力は窓を破棄したnextを返す。 */
export type SpectralPushResult =
  | Readonly<{
      kind: "frames";
      features: readonly SpectralFeatures[];
      next: SpectralStream;
    }>
  | Readonly<{ kind: "invalid-input"; next: SpectralStream }>;

type Pending = Readonly<{
  channels: readonly (readonly number[])[];
  startFrame: number;
  endFrame: number;
}>;
const empty: Pending = { channels: [], startFrame: 0, endFrame: 0 };
const invariantFailure = (): never => {
  throw new Error("Validated spectral window was rejected");
};
const stream = (
  config: SpectralConfig,
  dsp: DspPort<SpectralFeatures>,
  pending: Pending,
): SpectralStream => {
  const push = (block: PcmBlock): SpectralPushResult => {
    const collect = (): SpectralPushResult => {
      const continued =
        pending.endFrame === frameOf(block) &&
        pending.channels.length === block.channels.length;
      const startFrame = continued ? pending.startFrame : frameOf(block);
      const joined = block.channels.map((channel, index) => [
        ...(continued ? (pending.channels[index] ?? []) : []),
        ...channel,
      ]);
      const count = Math.max(
        0,
        Math.floor(
          ((joined[0]?.length ?? 0) - config.fftSize) / config.hopSize,
        ) + 1,
      );
      const features = Array.from({ length: count }, (_, index) => {
        const offset = index * config.hopSize;
        const result = dsp.analyze({
          channels: joined.map((channel) =>
            Float32Array.from(channel.slice(offset, offset + config.fftSize)),
          ),
          sampleRate: config.sampleRate,
          timeSeconds: (startFrame + offset) / config.sampleRate,
        });
        return result.kind === "features"
          ? result.features
          : invariantFailure();
      });
      return Object.freeze({
        kind: "frames",
        features: Object.freeze(features),
        next: stream(config, dsp, {
          channels: joined.map((channel) =>
            channel.slice(count * config.hopSize),
          ),
          startFrame: startFrame + count * config.hopSize,
          endFrame: frameOf(block) + (block.channels[0]?.length ?? 0),
        }),
      });
    };
    return validBlock(block) &&
      block.sampleRate === config.sampleRate &&
      validTiming(block)
      ? collect()
      : Object.freeze({
          kind: "invalid-input",
          next: stream(config, dsp, empty),
        });
  };
  return Object.freeze({ push });
};

/** 任意長の連続PCMを重複窓へ組み立てる。gap・巻き戻し・ch数変更では部分窓を破棄する。 */
export const createSpectralStream = (
  config: SpectralConfig = defaultSpectralConfig,
): SpectralStream => {
  const checked = configuration(config);
  return stream(checked, windowDsp(checked), empty);
};
