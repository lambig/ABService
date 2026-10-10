import {
  createFeatureStream,
  defaultSpectralConfig,
} from "abservice-audio-dsp";
import type { FeatureStream } from "abservice-audio-dsp";

/** Exercise the analysis path before playback, then return its untouched empty snapshot. */
export const preparedStream = (sampleRate: number): FeatureStream => {
  const empty = createFeatureStream({ ...defaultSpectralConfig, sampleRate });
  const channels = [440, 2100].map((frequency) =>
    Float32Array.from(
      { length: 128 },
      (_, frame) =>
        0.1 * Math.sin((2 * Math.PI * frequency * frame) / sampleRate),
    ),
  );
  Array.from({ length: 128 }, (_, quantum) => quantum).reduce(
    (stream, quantum) =>
      stream.push({
        channels,
        sampleRate,
        timeSeconds: (quantum * 128) / sampleRate,
      }).next,
    empty,
  );
  return empty;
};
