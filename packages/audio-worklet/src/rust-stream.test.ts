/* eslint-disable functional/immutable-data -- Tests advance privately owned candidate/reference streams and inject malformed PCM. */
/* eslint-disable @typescript-eslint/no-non-null-assertion -- Test fixtures and reference cardinality checks establish these array entries. */
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  createFeatureStream,
  defaultSpectralConfig,
} from "abservice-audio-dsp";
import type { AudioFeatures } from "abservice-audio-dsp";
import { createRustStream, prepareRustStream } from "./rust-stream";

const module = new WebAssembly.Module(
  readFileSync(new URL("./generated/audio-kernel.wasm", import.meta.url)),
);
const close = (
  actual: AudioFeatures | null,
  expected: readonly AudioFeatures[],
): void => {
  expect(actual === null).toBe(expected.length === 0);
  (expected.length === 0
    ? []
    : (Object.keys(expected[0]!) as (keyof AudioFeatures)[])
  ).forEach((key) => {
    const value = expected[0]![key];
    expect(Math.abs(actual![key] - value)).toBeLessThanOrEqual(
      1e-8 * Math.max(1, Math.abs(value)),
    );
  });
};

describe("production RustFFT SIMD asset", () => {
  it.each([44100, 48000, 96000])(
    "matches the reference through signal, channel and timeline changes at %i Hz",
    (rate) => {
      const candidate = prepareRustStream(module, rate);
      const fresh = () =>
        createFeatureStream({ ...defaultSpectralConfig, sampleRate: rate });
      const state = {
        reference: fresh(),
        retained: null as AudioFeatures | null,
        saved: null as AudioFeatures | null,
      };
      Array.from({ length: 640 }, (_, quantum) => quantum).forEach(
        (quantum) => {
          (quantum === 400
            ? () => {
                candidate.reset();
                state.reference = fresh();
              }
            : () => undefined)();
          const kind = Math.floor(quantum / 80) % 6;
          const channels = Array.from(
            { length: (Math.floor(quantum / 160) % 2) + 1 },
            (_, channel) =>
              Float32Array.from({ length: 128 }, (_, i) => {
                const frame = quantum * 128 + i;
                return kind === 0
                  ? 0
                  : kind === 1
                    ? 0.5
                    : kind === 2
                      ? frame % 2
                        ? -0.5
                        : 0.5
                      : kind === 3
                        ? frame % 509 === 3
                          ? 1
                          : 0
                        : Math.sin(frame * (0.117 + channel * 0.013)) *
                          (kind === 4 ? 0.7 : 1.4);
              }),
          );
          (quantum === 350
            ? () => {
                channels[0]![3] = NaN;
              }
            : () => undefined)();
          const frame = quantum * 128 + (quantum >= 250 ? 12345 : 0);
          const original = channels.map((channel) =>
            Float32Array.from(channel),
          );
          const expected = state.reference.push({
            channels,
            sampleRate: rate,
            timeSeconds: frame / rate,
          });
          state.reference = expected.next;
          const actual = candidate.push(channels, frame);
          close(actual, expected.features);
          expect(channels).toEqual(original);
          expect(state.retained).toEqual(state.saved);
          (actual === null
            ? () => undefined
            : () => {
                state.retained = actual;
                state.saved = { ...actual };
                expect(Object.isFrozen(actual)).toBe(true);
              })();
        },
      );
      candidate.dispose();
    },
  );

  it("isolates instances, supports opposite stereo channels, and resets after disconnected input", () => {
    const first = createRustStream(module, 48000);
    const second = createRustStream(module, 48000);
    const channels = Array.from({ length: 2 }, (_, i) =>
      new Float32Array(128).fill(i % 2 ? -0.5 : 0.5),
    );
    const outputs = Array.from({ length: 16 }, (_, q) =>
      first.push(channels, q * 128),
    );
    expect(outputs[15]?.rms).toBeCloseTo(0.5, 10);
    expect(second.push(channels, 15 * 128)).toBeNull();
    expect(first.push([], 16 * 128)).toBeNull();
    expect(first.push(channels, 17 * 128)).toBeNull();
    first.dispose();
    first.dispose();
    first.reset();
    expect(first.push(channels, 0)).toBeNull();
    second.dispose();
  });

  it("fails unsupported block contracts and rejects invalid frames without stale history", () => {
    const stream = createRustStream(module, 8000);
    expect(() => stream.push([new Float32Array(64)], 0)).toThrow(
      "render block",
    );
    expect(() =>
      stream.push(
        Array.from({ length: 3 }, () => new Float32Array(128)),
        0,
      ),
    ).toThrow("render block");
    [NaN, Infinity, -1, 1.5].forEach((frame) => {
      expect(stream.push([new Float32Array(128)], frame)).toBeNull();
    });
    stream.dispose();
    expect(() => createRustStream(module, 0)).toThrow("sample rate");
    expect(() =>
      createRustStream(
        new WebAssembly.Module(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0])),
        48000,
      ),
    ).toThrow("exports");
  });

  it("keeps linear memory fixed across initialization, processing and repeated disposal", () => {
    const api = new WebAssembly.Instance(module).exports as {
      memory: WebAssembly.Memory;
      streamInitialize: (rate: number) => void;
      push: (frame: number, channels: number) => number;
      reset: () => void;
      dispose: () => void;
    };
    const memory = api.memory.buffer;
    Array.from({ length: 24 }).forEach(() => {
      api.streamInitialize(48000);
      api.push(0, 2);
      api.reset();
      api.dispose();
    });
    expect(api.memory.buffer).toBe(memory);
    expect(memory.byteLength).toBe(2097152);
    expect(() => api.memory.grow(1)).toThrow();
  });
});
