/* eslint-disable functional/immutable-data -- The fake host records messages and advances the real processor's frame clock. */
import { readFileSync } from "node:fs";
import { afterEach, expect, it, vi } from "vitest";

type Message = {
  kind?: string;
  epoch?: number;
  features?: { rms: number; onset: number };
};
const kernel = new WebAssembly.Module(
  readFileSync(new URL("./generated/audio-kernel.wasm", import.meta.url)),
);
const messages: Message[] = [];
class Host {
  readonly port = {
    onmessage: (() => {}) as (event: MessageEvent<unknown>) => void,
    postMessage: (message: Message): void => {
      messages.push(message);
    },
  };
}
type Processor = Host & { process: (inputs: Float32Array[][]) => boolean };
const registered: {
  create?: new (options: {
    processorOptions: { kernel: WebAssembly.Module };
  }) => Processor;
} = {};

afterEach(() => {
  vi.unstubAllGlobals();
});

it("warms without leaking synthetic features, resets epochs and releases the instance", async () => {
  vi.stubGlobal("AudioWorkletProcessor", Host);
  vi.stubGlobal("sampleRate", 48000);
  vi.stubGlobal("currentFrame", 0);
  vi.stubGlobal(
    "registerProcessor",
    (_name: string, create: NonNullable<typeof registered.create>) => {
      registered.create = create;
    },
  );
  await import("./features-processor");
  const Create = registered.create as NonNullable<typeof registered.create>;
  const processor = new Create({ processorOptions: { kernel } });
  expect(messages).toEqual([{ kind: "ready" }]);
  const render = (start: number, count: number, value: number): void => {
    Array.from({ length: count }, (_, i) => i).forEach((i) => {
      vi.stubGlobal("currentFrame", (start + i) * 128);
      expect(processor.process([[new Float32Array(128).fill(value)]])).toBe(
        true,
      );
    });
  };
  render(0, 15, 0);
  expect(messages).toHaveLength(1);
  render(15, 17, 0);
  expect(messages.at(-1)?.features?.rms).toBe(0);
  expect(messages.at(-1)?.features?.onset).toBe(0);
  processor.port.onmessage({ data: { epoch: 7 } } as MessageEvent<unknown>);
  const before = messages.length;
  render(32, 15, 0.5);
  expect(messages).toHaveLength(before);
  render(47, 17, 0.5);
  expect(messages.at(-1)?.epoch).toBe(7);
  expect(messages.at(-1)?.features?.rms).toBeCloseTo(0.5, 10);
  processor.port.onmessage({
    data: { kind: "dispose" },
  } as MessageEvent<unknown>);
  expect(processor.process([[]])).toBe(false);
});
