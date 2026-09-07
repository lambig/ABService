/* eslint-disable functional/immutable-data -- AudioWorklet owns its window state and the engine-owned output buffers for this callback. */
import { collectRms, emptyWindow, intervalFrames } from "./accumulator";
import type { RmsWindow } from "./accumulator";

class RmsProcessor extends AudioWorkletProcessor {
  private window: RmsWindow = emptyWindow;
  private readonly interval: number;

  constructor(options: { processorOptions?: { notificationHz?: number } }) {
    super();
    this.interval = intervalFrames(
      sampleRate,
      options.processorOptions?.notificationHz ?? 30,
    );
  }

  process(inputs: Float32Array[][], outputs: Float32Array[][]): boolean {
    const channels = inputs[0] ?? [];
    const result = collectRms(
      { channels, sampleRate, timeSeconds: currentFrame / sampleRate },
      this.window,
      this.interval,
    );
    this.window = result.window;
    outputs[0]?.forEach((output, index) => {
      const source = result.valid ? channels[index] : undefined;
      const copy =
        source === undefined
          ? () => {
              output.fill(0);
            }
          : () => {
              output.set(source);
            };
      copy();
    });
    const notify =
      result.features === null
        ? () => undefined
        : () => {
            this.port.postMessage(result.features);
          };
    notify();
    return true;
  }
}
registerProcessor("abservice-rms", RmsProcessor);
