import {
  silentAudioFeatures,
  type AudioFeatureExtractor,
  type AudioFeatures,
  type PcmBlock,
} from './index.js';

const square = (sample: number): number => sample * sample;

const channelEnergy = (channel: Float32Array): number =>
  channel.length === 0
    ? 0
    : channel.reduce((sum, sample) => sum + square(sample), 0) / channel.length;

const mean = (values: readonly number[]): number =>
  values.length === 0
    ? 0
    : values.reduce((sum, value) => sum + value, 0) / values.length;

const clampUnit = (value: number): number => Math.min(1, Math.max(0, value));

export const referenceRms = (channels: readonly Float32Array[]): number =>
  clampUnit(Math.sqrt(mean(channels.map(channelEnergy))));

export class ReferenceRmsExtractor implements AudioFeatureExtractor {
  extract(block: PcmBlock): AudioFeatures {
    const rms = referenceRms(block.channels);

    return rms === 0
      ? silentAudioFeatures(block.timeSeconds)
      : {
          ...silentAudioFeatures(block.timeSeconds),
          rms,
        };
  }
}
