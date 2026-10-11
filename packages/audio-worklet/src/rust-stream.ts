/* eslint-disable functional/immutable-data -- This private adapter exclusively owns a WASM instance and its transfer memory; output is copied before returning. */
import type { AudioFeatures } from "abservice-audio-dsp";

type Kernel = WebAssembly.Exports & {
  memory: WebAssembly.Memory;
  abiVersion: () => number;
  streamInitialize: (rate: number) => void;
  transferPointer: () => number;
  featuresPointer: () => number;
  push: (frame: number, channels: number) => number;
  reset: () => void;
  dispose: () => void;
};

/** Mutable, node-private state; intentionally distinct from public immutable FeatureStream. */
export type RustStream = Readonly<{
  push: (
    channels: readonly Readonly<Float32Array>[],
    frame: number,
  ) => AudioFeatures | null;
  reset: () => void;
  dispose: () => void;
}>;

const required = [
  "abiVersion",
  "streamInitialize",
  "transferPointer",
  "featuresPointer",
  "push",
  "reset",
  "dispose",
] as const;
const reject = (message: string): never => {
  throw new Error(message);
};

/** Instantiate a precompiled SIMD module. PCM history and FFT scratch remain in Rust. */
export const createRustStream = (
  module: WebAssembly.Module,
  rate: number,
): RustStream => {
  const exports = new WebAssembly.Instance(module).exports;
  const valid =
    exports.memory instanceof WebAssembly.Memory &&
    required.every((name) => typeof exports[name] === "function");
  const api = valid
    ? (exports as Kernel)
    : reject("Invalid audio kernel exports");
  (api.abiVersion() === 1
    ? () => undefined
    : () => reject("Unsupported audio kernel ABI"))();
  (Number.isFinite(rate) && rate >= 8000 && rate <= 384000
    ? () => undefined
    : () => reject("Unsupported audio sample rate"))();
  api.streamInitialize(rate);
  const transfer = new Float32Array(
    api.memory.buffer,
    api.transferPointer(),
    128 * 2,
  );
  const values = new Float64Array(
    api.memory.buffer,
    api.featuresPointer(),
    7,
  ) as Float64Array & {
    readonly 0: number;
    readonly 1: number;
    readonly 2: number;
    readonly 3: number;
    readonly 4: number;
    readonly 5: number;
    readonly 6: number;
  };
  const state = { disposed: false };
  const reset = (): void => {
    (state.disposed
      ? () => undefined
      : () => {
          api.reset();
        })();
  };
  const read = (): AudioFeatures =>
    Object.freeze({
      timeSeconds: values[0],
      rms: values[1],
      lowEnergy: values[2],
      midEnergy: values[3],
      highEnergy: values[4],
      onset: values[5],
      spectralCentroidHz: values[6],
    });
  const push = (
    channels: readonly Readonly<Float32Array>[],
    frame: number,
  ): AudioFeatures | null => {
    const supported =
      channels.length <= 2 &&
      channels.every((channel) => channel.length === 128);
    (supported
      ? () => undefined
      : () => reject("Unsupported audio render block"))();
    const process = (): AudioFeatures | null => {
      channels.forEach((channel, index) => {
        transfer.set(channel, index * 128);
      });
      return api.push(frame, channels.length) === 1 ? read() : null;
    };
    return state.disposed
      ? null
      : Number.isSafeInteger(frame) && frame >= 0 && channels.length > 0
        ? process()
        : (reset(), null);
  };
  return Object.freeze({
    push,
    reset,
    dispose: (): void => {
      (state.disposed
        ? () => undefined
        : () => {
            api.dispose();
          })();
      state.disposed = true;
    },
  });
};

/** Warm before connecting the node to media; reset discards every synthetic frame. */
export const prepareRustStream = (
  module: WebAssembly.Module,
  rate: number,
): RustStream => {
  const stream = createRustStream(module, rate);
  const channels = [440, 2100].map((frequency) =>
    Float32Array.from(
      { length: 128 },
      (_, frame) => 0.1 * Math.sin((2 * Math.PI * frequency * frame) / rate),
    ),
  );
  Array.from({ length: 1024 }, (_, quantum) => quantum).forEach((quantum) =>
    stream.push(channels, quantum * 128),
  );
  stream.reset();
  return stream;
};
