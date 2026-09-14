/* eslint-disable functional/immutable-data -- Worklet owns stream state and MessagePort; borrowed PCM stays on the rendering thread. */
import {
  createFeatureStream,
  defaultSpectralConfig,
} from "abservice-audio-dsp";
import type { AudioFeatures } from "abservice-audio-dsp";
import { intervalFrames } from "./accumulator";

class FeaturesProcessor extends AudioWorkletProcessor {
  private stream = createFeatureStream({
    ...defaultSpectralConfig,
    sampleRate,
  });
  private epoch = 0;
  private phase = 0;
  private peak = 0;
  private latest: AudioFeatures | null = null;
  private readonly interval: number;

  constructor(options: { processorOptions?: { notificationHz?: number } }) {
    super();
    this.interval = intervalFrames(
      sampleRate,
      options.processorOptions?.notificationHz ?? 30,
    );
    this.port.onmessage = (event: MessageEvent<unknown>): void => {
      const data = event.data as { epoch?: unknown } | null;
      const reset = (): void => {
        this.epoch = data?.epoch as number;
        this.stream = createFeatureStream({
          ...defaultSpectralConfig,
          sampleRate,
        });
        this.phase = 0;
        this.peak = 0;
        this.latest = null;
      };
      (typeof data?.epoch === "number" && Number.isSafeInteger(data.epoch)
        ? reset
        : () => undefined)();
    };
  }

  process(inputs: Float32Array[][]): boolean {
    const channels = inputs[0] ?? [];
    const result = this.stream.push({
      channels,
      sampleRate,
      timeSeconds: currentFrame / sampleRate,
    });
    this.stream = result.next;
    result.features.forEach((features) => {
      this.latest = features;
      this.peak = Math.max(this.peak, features.onset);
    });
    this.phase += channels[0]?.length ?? 0;
    const latest = this.latest;
    const notify = (): void => {
      this.port.postMessage({
        epoch: this.epoch,
        features: { ...latest, onset: this.peak },
      });
      this.phase %= this.interval;
      this.peak = 0;
      this.latest = null;
    };
    (this.phase >= this.interval && latest !== null
      ? notify
      : () => undefined)();
    return true;
  }
}
registerProcessor("abservice-features", FeaturesProcessor);
