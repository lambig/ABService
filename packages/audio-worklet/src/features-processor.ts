/* eslint-disable functional/immutable-data -- Worklet owns stream state and MessagePort; borrowed PCM stays on the rendering thread. */
import type { AudioFeatures } from "abservice-audio-dsp";
import { intervalFrames } from "./accumulator";
import { createRustStream } from "./rust-stream";
import type { RustStream } from "./rust-stream";

class FeaturesProcessor extends AudioWorkletProcessor {
  private readonly stream: RustStream;
  private active = true;
  private epoch = 0;
  private phase = 0;
  private peak = 0;
  private latest: AudioFeatures | null = null;
  /* Date.now は 1 ms 分解能のため、1回ごとではなく通知の区間の合計だけを持つ。 */
  private busyMs = 0;
  private busyFrames = 0;
  private readonly interval: number;

  constructor(options: {
    processorOptions?: { notificationHz?: number; kernel?: WebAssembly.Module };
  }) {
    super();
    this.stream = createRustStream(
      options.processorOptions?.kernel as WebAssembly.Module,
      sampleRate,
    );
    this.interval = intervalFrames(
      sampleRate,
      options.processorOptions?.notificationHz ?? 30,
    );
    this.port.onmessage = (event: MessageEvent<unknown>): void => {
      const data = event.data as { epoch?: unknown; kind?: unknown } | null;
      (data?.kind === "dispose"
        ? () => {
            this.stream.dispose();
            this.active = false;
          }
        : () => undefined)();
      const reset = (): void => {
        this.epoch = data?.epoch as number;
        this.reset();
      };
      (typeof data?.epoch === "number" && Number.isSafeInteger(data.epoch)
        ? reset
        : () => undefined)();
    };
    /* Exercise the same analysis/accumulation path before connecting audible input.
     * Suppress delivery while warming so no synthetic features escape the node. */
    const channels = [440, 2100].map((frequency) =>
      Float32Array.from(
        { length: 128 },
        (_, frame) =>
          0.1 * Math.sin((2 * Math.PI * frequency * frame) / sampleRate),
      ),
    );
    Array.from({ length: 1024 }, (_, quantum) => quantum).forEach((quantum) => {
      this.analyze(channels, quantum * 128, false);
    });
    this.reset();
    this.port.postMessage({ kind: "ready" });
  }

  private reset(): void {
    this.stream.reset();
    this.phase = 0;
    this.peak = 0;
    this.latest = null;
    this.busyMs = 0;
    this.busyFrames = 0;
  }

  private analyze(
    channels: Float32Array[],
    frame: number,
    deliver: boolean,
  ): void {
    const begin = Date.now();
    const features = this.stream.push(channels, frame);
    (features === null
      ? () => undefined
      : () => {
          this.latest = features;
          this.peak = Math.max(this.peak, features.onset);
        })();
    this.busyMs += Date.now() - begin;
    this.busyFrames += channels[0]?.length ?? 0;
    this.phase += channels[0]?.length ?? 0;
    const latest = this.latest;
    const notify = (): void => {
      const message = {
        epoch: this.epoch,
        features: { ...latest, onset: this.peak },
        load: { busyMs: this.busyMs, frames: this.busyFrames },
      };
      (deliver
        ? () => {
            this.port.postMessage(message);
          }
        : () => undefined)();
      this.phase %= this.interval;
      this.peak = 0;
      this.latest = null;
      this.busyMs = 0;
      this.busyFrames = 0;
    };
    (this.phase >= this.interval && latest !== null
      ? notify
      : () => undefined)();
  }

  process(inputs: Float32Array[][]): boolean {
    this.analyze(inputs[0] ?? [], currentFrame, true);
    return this.active;
  }
}
registerProcessor("abservice-features", FeaturesProcessor);
