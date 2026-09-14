import { createSpectralStream, defaultSpectralConfig } from "./spectral";
import type { SpectralConfig, SpectralStream } from "./spectral";
import type { AudioFeatures, PcmBlock, SpectralFeatures } from "./index";

const energies = (frame: SpectralFeatures): readonly number[] =>
  [frame.lowEnergy, frame.midEnergy, frame.highEnergy].map(Math.sqrt);

/** 3帯域の振幅増分の平均。低下は0、立ち上がりは0..1。beat判定やspectral flux全bin版ではない。 */
export const onsetStrength = (
  current: SpectralFeatures,
  previous: SpectralFeatures | null,
): number => {
  const before = previous === null ? [0, 0, 0] : energies(previous);
  return Math.min(
    1,
    energies(current).reduce(
      (sum, value, index) =>
        sum + Math.max(0, value - (before[index] ?? 0) - 1e-6),
      0,
    ) / 3,
  );
};

/** PCMは呼び出し中だけ借用し、次の状態と時刻付きの完全な特徴量を返す。 */
export type FeatureStream = Readonly<{
  push: (block: PcmBlock) => Readonly<{
    features: readonly AudioFeatures[];
    next: FeatureStream;
  }>;
}>;
const stream = (
  config: SpectralConfig,
  spectral: SpectralStream,
  previous: SpectralFeatures | null,
  endFrame: number | null,
  channelCount: number,
): FeatureStream => ({
  push: (block) => {
    const continued =
      endFrame === Math.round(block.timeSeconds * block.sampleRate) &&
      channelCount === block.channels.length;
    const result = spectral.push(block);
    const frames = result.kind === "frames" ? result.features : [];
    const before = continued ? previous : null;
    const features = frames.map((frame, index) =>
      Object.freeze({
        ...frame,
        onset: onsetStrength(
          frame,
          index === 0 ? before : (frames[index - 1] ?? null),
        ),
      }),
    );
    return Object.freeze({
      features: Object.freeze(features),
      next: stream(
        config,
        result.next,
        result.kind === "frames" ? (frames.at(-1) ?? before) : null,
        result.kind === "frames"
          ? Math.round(block.timeSeconds * block.sampleRate) +
              (block.channels[0]?.length ?? 0)
          : null,
        block.channels.length,
      ),
    });
  },
});

/** #305の周波数解析に帯域振幅の立ち上がりを足すJS参照実装。seek時は新しいstreamで始める。 */
export const createFeatureStream = (
  config: SpectralConfig = defaultSpectralConfig,
): FeatureStream => stream(config, createSpectralStream(config), null, null, 0);
