export type AudioFeatures = Readonly<{
  timeSeconds: number;
  rms: number;
  lowEnergy: number;
  midEnergy: number;
  highEnergy: number;
  onset: number;
  spectralCentroidHz: number;
}>;

export type PcmBlock = Readonly<{
  sampleRateHz: number;
  timeSeconds: number;
  channels: readonly Float32Array[];
}>;

export interface AudioFeatureExtractor {
  extract(block: PcmBlock): AudioFeatures;
}

export const silentAudioFeatures = (timeSeconds: number): AudioFeatures => ({
  timeSeconds,
  rms: 0,
  lowEnergy: 0,
  midEnergy: 0,
  highEnergy: 0,
  onset: 0,
  spectralCentroidHz: 0,
});
