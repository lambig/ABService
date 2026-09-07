import { rmsDsp } from "abservice-audio-dsp";
import type { PcmBlock, RmsFeatures } from "abservice-audio-dsp";

/** 通知窓の統計だけを保持する。PCMへの参照は保持しない。 */
export type RmsWindow = Readonly<{
  power: number;
  frames: number;
  phase: number;
  endFrame: number | null;
}>;

/** 無入力・不正入力・時刻の不連続は窓を破棄する。 */
export const emptyWindow: RmsWindow = Object.freeze({
  power: 0,
  frames: 0,
  phase: 0,
  endFrame: null,
});

/** 不正な設定は音声資源を確保する前に拒否する。 */
export const intervalFrames = (
  sampleRate: number,
  notificationHz: number,
): number => {
  const valid =
    Number.isFinite(sampleRate) &&
    sampleRate > 0 &&
    Number.isFinite(notificationHz) &&
    notificationHz >= 1 &&
    notificationHz <= 60;
  return valid ? Math.ceil(sampleRate / notificationHz) : invalidRate();
};
const invalidRate = (): never => {
  throw new RangeError("Notification frequency must be between 1 and 60 Hz");
};

/** 各ブロックの平均パワーをフレーム数で重み付けし、通知窓のRMSを求める。 */
export const collectRms = (
  block: PcmBlock,
  previous: RmsWindow,
  interval: number,
): Readonly<{
  valid: boolean;
  window: RmsWindow;
  features: RmsFeatures | null;
}> => {
  const result = rmsDsp.analyze(block);
  const collect = (rms: number) => {
    const startFrame = Math.round(block.timeSeconds * block.sampleRate);
    const base = previous.endFrame === startFrame ? previous : emptyWindow;
    const length = block.channels[0]?.length ?? 0;
    const frames = base.frames + length;
    const power = base.power + rms * rms * length;
    const phase = base.phase + length;
    const due = phase >= interval;
    const endFrame = startFrame + length;
    return {
      valid: true,
      window: {
        power: due ? 0 : power,
        frames: due ? 0 : frames,
        phase: phase % interval,
        endFrame,
      },
      features: due
        ? Object.freeze({
            timeSeconds: endFrame / block.sampleRate,
            rms: Math.sqrt(power / frames),
          })
        : null,
    };
  };
  return result.kind === "features"
    ? collect(result.features.rms)
    : { valid: false, window: emptyWindow, features: null };
};
