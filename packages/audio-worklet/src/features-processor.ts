/* eslint-disable functional/immutable-data -- Worklet owns stream state and MessagePort; borrowed PCM stays on the rendering thread. */
import type { AudioFeatures } from "abservice-audio-dsp";
import { intervalFrames } from "./accumulator";
import { preparedStream } from "./prepared-stream";

class FeaturesProcessor extends AudioWorkletProcessor {
  private readonly empty = preparedStream(sampleRate);
  private stream = this.empty;
  private epoch = 0;
  private phase = 0;
  private peak = 0;
  private latest: AudioFeatures | null = null;
  /* Date.now は 1 ms 分解能のため、1回ごとではなく通知の区間の合計だけを持つ。 */
  private busyMs = 0;
  private busyFrames = 0;
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
        this.stream = this.empty;
        this.phase = 0;
        this.peak = 0;
        this.latest = null;
        this.busyMs = 0;
        this.busyFrames = 0;
      };
      (typeof data?.epoch === "number" && Number.isSafeInteger(data.epoch)
        ? reset
        : () => undefined)();
    };
    this.port.postMessage({ kind: "ready" });
  }

  process(inputs: Float32Array[][]): boolean {
    const channels = inputs[0] ?? [];
    const begin = Date.now();
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
    this.busyMs += Date.now() - begin;
    this.busyFrames += channels[0]?.length ?? 0;
    this.phase += channels[0]?.length ?? 0;
    const latest = this.latest;
    const notify = (): void => {
      this.port.postMessage({
        epoch: this.epoch,
        features: { ...latest, onset: this.peak },
        load: { busyMs: this.busyMs, frames: this.busyFrames },
      });
      this.phase %= this.interval;
      this.peak = 0;
      this.latest = null;
      this.busyMs = 0;
      this.busyFrames = 0;
    };
    (this.phase >= this.interval && latest !== null
      ? notify
      : () => undefined)();
    return true;
  }
}
registerProcessor("abservice-features", FeaturesProcessor);
