// WORKAROUND: TypeScriptのDOM libに含まれないAudioWorkletGlobalScopeの使用部分だけを宣言する。
declare const currentFrame: number;
declare const sampleRate: number;
declare abstract class AudioWorkletProcessor {
  readonly port: MessagePort;
  abstract process(
    inputs: Float32Array[][],
    outputs: Float32Array[][],
  ): boolean;
}
declare function registerProcessor(
  name: string,
  processor: new (options: {
    processorOptions?: { notificationHz?: number };
  }) => AudioWorkletProcessor,
): void;
declare module "*?worker&url" {
  const url: string;
  export default url;
}
