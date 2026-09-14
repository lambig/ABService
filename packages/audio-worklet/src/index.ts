/* eslint-disable functional/immutable-data -- This adapter owns Web Audio resources, MessagePort listeners and idempotent session disposal. */
import type { RmsFeatures } from "abservice-audio-dsp";
import { intervalFrames } from "./accumulator";
import processorUrl from "./processor.ts?worker&url";

/** 検証用の合成音源。左右逆相でもRMSが相殺されないことを確認できる。 */
export type Fixture = "mono" | "stereo" | "opposed" | "left-only" | "silence";

/** 資源の所有者をsessionへ閉じ込め、呼び出し元へ音声graphを公開しない。 */
export type RmsSession = Readonly<{ stop: () => Promise<void> }>;

/** P1の通知設定。特徴量以外の音声データをcallbackへ渡さない。 */
export type SessionOptions = Readonly<{
  fixture: Fixture;
  notificationHz: number;
  onFeatures: (features: RmsFeatures) => void;
  onError: (error: Error) => void;
}>;

const snapshot = (value: unknown): RmsFeatures | null => {
  const data = value as Partial<RmsFeatures> | null;
  return typeof data === "object" &&
    data !== null &&
    typeof data.timeSeconds === "number" &&
    Number.isFinite(data.timeSeconds) &&
    data.timeSeconds >= 0 &&
    typeof data.rms === "number" &&
    Number.isFinite(data.rms) &&
    data.rms >= 0 &&
    data.rms <= 1
    ? Object.freeze({ timeSeconds: data.timeSeconds, rms: data.rms })
    : null;
};
const synthesize = (context: AudioContext, fixture: Fixture): AudioBuffer => {
  const count = ["mono", "silence"].includes(fixture) ? 1 : 2;
  const buffer = context.createBuffer(
    count,
    context.sampleRate,
    context.sampleRate,
  );
  Array.from({ length: count }, (_, index) => index).forEach((channel) => {
    const gain =
      fixture === "silence"
        ? 0
        : channel === 0
          ? 1
          : fixture === "left-only"
            ? 0
            : fixture === "opposed"
              ? -1
              : 1;
    buffer.copyToChannel(
      Float32Array.from(
        { length: buffer.length },
        (_, frame) =>
          gain *
          0.1 *
          Math.sin((2 * Math.PI * 440 * frame) / context.sampleRate),
      ),
      channel,
    );
  });
  return buffer;
};

/** resumeが保留されたままでも、abortによって初期化の呼び出しを解放する。 */
const untilAborted = async (
  operation: () => Promise<void>,
  signal: AbortSignal,
): Promise<void> => {
  signal.throwIfAborted();
  await new Promise<void>((resolve, reject) => {
    const cancel = (): void => {
      reject(
        new DOMException("Audio session initialization aborted", "AbortError"),
      );
    };
    signal.addEventListener("abort", cancel, { once: true });
    void operation().then(
      () => {
        signal.removeEventListener("abort", cancel);
        resolve();
      },
      (error: unknown) => {
        signal.removeEventListener("abort", cancel);
        reject(error instanceof Error ? error : new Error(String(error)));
      },
    );
  });
};

/**
 * ユーザー操作から呼び、合成音源→Worklet→出力を開始する。
 * abortは初期化中も有効。stopは冪等で、通知を遮断してcontextを閉じる。
 */
export const startRmsSession = async (
  options: SessionOptions,
  signal: AbortSignal,
): Promise<RmsSession> => {
  intervalFrames(48000, options.notificationHz);
  signal.throwIfAborted();
  const context = new AudioContext();
  const resources: {
    node?: AudioWorkletNode;
    source?: AudioBufferSourceNode;
    stopping?: Promise<void>;
  } = {};
  const stop = (): Promise<void> => {
    const close = (): Promise<void> => {
      signal.removeEventListener("abort", abort);
      resources.source?.stop();
      resources.source?.disconnect();
      resources.node?.port.close();
      resources.node?.disconnect();
      return context.state === "closed" ? Promise.resolve() : context.close();
    };
    resources.stopping ??= close();
    return resources.stopping;
  };
  const abort = (): void => {
    void stop();
  };
  signal.addEventListener("abort", abort, { once: true });
  try {
    await untilAborted(() => context.resume(), signal);
    await untilAborted(
      () => context.audioWorklet.addModule(processorUrl),
      signal,
    );
    signal.throwIfAborted();
    const source = context.createBufferSource();
    source.buffer = synthesize(context, options.fixture);
    source.loop = true;
    const node = new AudioWorkletNode(context, "abservice-rms", {
      numberOfInputs: 1,
      numberOfOutputs: 1,
      outputChannelCount: [source.buffer.numberOfChannels],
      channelCountMode: "max",
      channelInterpretation: "discrete",
      processorOptions: { notificationHz: options.notificationHz },
    });
    resources.node = node;
    node.port.onmessage = (event: MessageEvent<unknown>): void => {
      const feature =
        resources.stopping === undefined ? snapshot(event.data) : null;
      const deliver =
        feature === null
          ? () => undefined
          : () => {
              options.onFeatures(feature);
            };
      deliver();
    };
    node.onprocessorerror = (): void => {
      const report =
        resources.stopping === undefined
          ? () => {
              void stop();
              options.onError(new Error("AudioWorklet processor failed"));
            }
          : () => undefined;
      report();
    };
    source.connect(node).connect(context.destination);
    source.start();
    resources.source = source;
    return Object.freeze({ stop });
  } catch (error) {
    await stop();
    throw error;
  }
};
