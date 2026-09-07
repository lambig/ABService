/** 描画へ渡す v0 の完全な特徴量。時刻は AudioContext の秒、centroid は Hz。 */
export type AudioFeatures = Readonly<{
  timeSeconds: number;
  rms: number;
  lowEnergy: number;
  midEnergy: number;
  highEnergy: number;
  onset: number;
  spectralCentroidHz: number;
}>;

/** P1 の観測値。未実装の周波数特徴量をゼロで偽装しない。 */
export type RmsFeatures = Pick<AudioFeatures, "timeSeconds" | "rms">;

/** 同期呼び出し中のみ借用する planar PCM。チャンネル長は等しく、1つ以上のフレームを持つ。 */
export type PcmBlock = Readonly<{
  channels: readonly Readonly<Float32Array>[];
  sampleRate: number;
  timeSeconds: number;
}>;

/** 不正な入力を特徴量として通知しないための検証結果。 */
export type DspResult<T> =
  | Readonly<{ kind: "features"; features: T }>
  | Readonly<{ kind: "invalid-input" }>;

/** JS/WASM 共通の同期境界。実装は借用 PCM を保持・変更しない。 */
export type DspPort<T extends RmsFeatures = AudioFeatures> = Readonly<{
  analyze: (block: PcmBlock) => DspResult<T>;
}>;

const nonNegativeFinite = (value: number): boolean =>
  Number.isFinite(value) && value >= 0;
const unitInterval = (value: number): boolean =>
  nonNegativeFinite(value) && value <= 1;
const invalidInput = Object.freeze({ kind: "invalid-input" } as const);
const snapshot = <T extends RmsFeatures>(features: T): DspResult<Readonly<T>> =>
  Object.freeze({ kind: "features", features: Object.freeze({ ...features }) });

/** v0 の値域を検証し、呼び出し元から独立した immutable snapshot を生成する。 */
export const audioFeatures = (
  value: AudioFeatures,
): DspResult<AudioFeatures> =>
  [
    value.rms,
    value.lowEnergy,
    value.midEnergy,
    value.highEnergy,
    value.onset,
  ].every(unitInterval) &&
  nonNegativeFinite(value.timeSeconds) &&
  nonNegativeFinite(value.spectralCentroidHz)
    ? snapshot(value)
    : invalidInput;

const validBlock = (block: PcmBlock): boolean =>
  [1, 2].includes(block.channels.length) &&
  Number.isFinite(block.sampleRate) &&
  block.sampleRate > 0 &&
  nonNegativeFinite(block.timeSeconds) &&
  block.channels.every(
    (channel) =>
      channel.length > 0 &&
      channel.length === block.channels[0]?.length &&
      channel.every(Number.isFinite),
  );

const clippedSquare = (value: number): number =>
  Math.min(1, Math.abs(value)) ** 2;
const meanSquare = (channel: Readonly<Float32Array>): number =>
  channel.reduce((sum, value) => sum + clippedSquare(value), 0) /
  channel.length;
const rms = (channels: PcmBlock["channels"]): number =>
  Math.sqrt(
    channels.reduce((sum, channel) => sum + meanSquare(channel), 0) /
      channels.length,
  );

/**
 * チャンネルの平均パワーから RMS を求める stateless な参照実装。
 * 左右逆相でも相殺せず、片 ch の信号は mono の 1/sqrt(2) になる。
 * 各サンプルを [-1, 1] に制限し、窓・平滑化は適用しない。
 */
export const rmsDsp: DspPort<RmsFeatures> = Object.freeze({
  analyze: (block: PcmBlock): DspResult<RmsFeatures> =>
    validBlock(block)
      ? snapshot({ timeSeconds: block.timeSeconds, rms: rms(block.channels) })
      : invalidInput,
});
