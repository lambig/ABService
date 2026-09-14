/* eslint-disable functional/immutable-data -- Web Audio graph, epoch and disposal are confined to this media adapter. */
import { audioFeatures } from "abservice-audio-dsp";
import type { AudioFeatures } from "abservice-audio-dsp";
import processorUrl from "./features-processor.ts?worker&url";

/** 解析を失っても音声の直接出力は維持し、障害を表示側へ通知する。 */
export type MediaAnalysisOptions = Readonly<{
  onFeatures: (features: AudioFeatures) => void;
  onReset: () => void;
  onError: (error: Error) => void;
}>;

/** 借用したmediaとsignalに寿命を合わせる。playはユーザー操作の同一呼び出し内で行う。 */
export const connectMediaAnalysis = (
  media: HTMLAudioElement,
  signal: AbortSignal,
  options: MediaAnalysisOptions,
): Readonly<{ play: () => void; pause: () => void; seek: () => void }> => {
  const state: {
    context: AudioContext | null;
    source: MediaElementAudioSourceNode | null;
    node: AudioWorkletNode | null;
    active: boolean;
    failed: boolean;
    epoch: number;
  } = {
    context: null,
    source: null,
    node: null,
    active: false,
    failed: false,
    epoch: 0,
  };
  const clearNode = (): void => {
    state.node?.port.close();
    state.node?.disconnect();
    state.node = null;
  };
  const fail = (error: unknown): void => {
    const report = (): void => {
      state.failed = true;
      clearNode();
      options.onReset();
      options.onError(
        error instanceof Error ? error : new Error(String(error)),
      );
    };
    (signal.aborted ? () => undefined : report)();
  };
  const reset = (): void => {
    state.epoch += 1;
    state.node?.port.postMessage({ epoch: state.epoch });
    options.onReset();
  };
  const receive = (event: MessageEvent<unknown>): void => {
    const data = event.data as { epoch?: unknown; features?: unknown } | null;
    const features = data?.features as AudioFeatures | null | undefined;
    const accept = (): void => {
      const result = audioFeatures(features as AudioFeatures);
      (result.kind === "features"
        ? () => {
            options.onFeatures(result.features);
          }
        : () => undefined)();
    };
    const blocked = [
      signal.aborted,
      state.failed,
      media.paused,
      media.seeking,
    ].some(Boolean);
    ((blocked ? false : state.active) &&
      data?.epoch === state.epoch &&
      typeof features === "object" &&
      features !== null
      ? accept
      : () => undefined)();
  };
  const initialize = (): AudioContext => {
    const context = new AudioContext();
    state.context = context;
    const source = context.createMediaElementSource(media);
    state.source = source;
    source.connect(context.destination);
    void context.audioWorklet
      .addModule(processorUrl)
      .then(() => {
        const attach = (): void => {
          const node = new AudioWorkletNode(context, "abservice-features", {
            numberOfInputs: 1,
            numberOfOutputs: 1,
            outputChannelCount: [1],
            channelCountMode: "max",
            channelInterpretation: "discrete",
            processorOptions: { notificationHz: 30 },
          });
          state.node = node;
          node.port.onmessage = receive;
          node.onprocessorerror = () => {
            fail(new Error("Audio analysis processor failed"));
          };
          node.port.postMessage({ epoch: state.epoch });
          /* The analysis output is silence; the media source is audible through the direct branch only. */
          source.connect(node).connect(context.destination);
        };
        (signal.aborted ? () => undefined : attach)();
      })
      .catch(fail);
    return context;
  };
  const play = (): void => {
    const start = (): void => {
      state.active = true;
      reset();
      try {
        const context = state.context ?? initialize();
        void context.resume().catch(fail);
      } catch (error) {
        fail(error);
      }
    };
    (signal.aborted ? () => undefined : start)();
  };
  const pause = (): void => {
    state.active = false;
    reset();
    const context = state.context;
    (context === null
      ? () => undefined
      : () => {
          void context.suspend().catch(fail);
        })();
  };
  const dispose = (): void => {
    state.active = false;
    clearNode();
    state.source?.disconnect();
    const context = state.context;
    state.context = null;
    (context === null
      ? () => undefined
      : () => {
          void context.close().catch(() => undefined);
        })();
  };
  signal.addEventListener("abort", dispose, { once: true });
  media.addEventListener("seeking", reset, { signal });
  media.addEventListener("seeked", reset, { signal });
  return Object.freeze({ play, pause, seek: reset });
};
