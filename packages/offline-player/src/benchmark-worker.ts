import { benchmarkFeatureStream } from "abservice-audio-dsp";

/** main thread で decode 済みの PCM。チャンネルの配列はコピーせずに移される。 */
export type BenchmarkRequest = Readonly<{
  channels: Float32Array[];
  sampleRate: number;
  maxQuanta: number;
}>;

/* The page's DOM types describe a window; only the two worker members used here are declared. */
const scope = globalThis as unknown as {
  addEventListener: (
    type: "message",
    listener: (event: MessageEvent<BenchmarkRequest>) => void,
  ) => void;
  postMessage: (message: unknown) => void;
};

/* The worker has performance.now, which the AudioWorklet lacks; the same DSP runs here to approximate its cost. */
scope.addEventListener("message", (event) => {
  const { channels, sampleRate, maxQuanta } = event.data;
  try {
    scope.postMessage({
      kind: "result",
      result: benchmarkFeatureStream(channels, {
        sampleRate,
        maxQuanta,
        now: () => performance.now(),
      }),
    });
  } catch (error) {
    scope.postMessage({ kind: "error", message: String(error) });
  }
});
