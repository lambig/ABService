import { describe, expect, it } from "vitest";
import {
  createFeatureStream,
  defaultSpectralConfig,
} from "abservice-audio-dsp";
import type { FeatureStream } from "abservice-audio-dsp";
import { preparedStream } from "./prepared-stream";

const analyze = (
  stream: FeatureStream,
  sampleRate: number,
  start: number,
  stereo: boolean,
) => {
  const channels = (stereo ? [330, 1800] : [330]).map((frequency) =>
    Float32Array.from(
      { length: 4096 },
      (_, frame) =>
        0.2 * Math.sin((2 * Math.PI * frequency * frame) / sampleRate),
    ),
  );
  return stream.push({ channels, sampleRate, timeSeconds: start });
};

describe("prepared stream", () => {
  it.each([44100, 48000])(
    "preparation and reset preserve empty-stream features at %i Hz",
    (sampleRate) => {
      const empty = preparedStream(sampleRate);
      const fresh = () =>
        createFeatureStream({ ...defaultSpectralConfig, sampleRate });
      const first = analyze(empty, sampleRate, 3, true);
      expect(first.features).toEqual(
        analyze(fresh(), sampleRate, 3, true).features,
      );
      const saved = structuredClone(first.features);
      /* Reset after consuming unrelated PCM, then switch channel count and seek time. */
      analyze(first.next, sampleRate, 8, true);
      expect(analyze(empty, sampleRate, 12, false).features).toEqual(
        analyze(fresh(), sampleRate, 12, false).features,
      );
      expect(first.features).toEqual(saved);
    },
  );
});
