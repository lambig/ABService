import { benchmarkRustAudio } from "abservice-audio-worklet/benchmark";

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

/* Worker timing measures the production kernel, not the Worklet's scheduling deadline. */
const measure = async (
  event: MessageEvent<BenchmarkRequest>,
): Promise<void> => {
  const { channels, sampleRate, maxQuanta } = event.data;
  try {
    scope.postMessage({
      kind: "result",
      result: await benchmarkRustAudio(channels, sampleRate, maxQuanta, () =>
        performance.now(),
      ),
    });
  } catch (error) {
    scope.postMessage({ kind: "error", message: String(error) });
  }
};
scope.addEventListener("message", (event) => {
  void measure(event);
});
