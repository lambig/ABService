import kernelUrl from "./generated/audio-kernel.wasm?url";

/** Compile away from the audio rendering thread; failure is handled by the media adapter. */
export const loadAudioKernel = async (
  signal: AbortSignal,
): Promise<WebAssembly.Module> => {
  const response = await fetch(kernelUrl, {
    signal,
    credentials: "same-origin",
  });
  return response.ok
    ? WebAssembly.compile(await response.arrayBuffer())
    : Promise.reject(
        new Error(`Audio kernel load failed: ${String(response.status)}`),
      );
};
