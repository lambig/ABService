/* eslint-disable functional/immutable-data -- Bounded waveform reduction and DOM/cache updates are isolated here. */
import type { LocalAssetResolver } from "abservice-player";
import { element, when } from "./dom";

type Envelope = Readonly<{ duration: number; peaks: readonly number[] }>;
const bins = 256;
const sampleRate = 8000;
const valid = (value: unknown): value is Envelope => {
  const data = value as Partial<Envelope> | null;
  return (
    data !== null &&
    typeof data === "object" &&
    Number.isFinite(data.duration) &&
    (data.duration ?? 0) > 0 &&
    Array.isArray(data.peaks) &&
    data.peaks.length === bins &&
    data.peaks.every(
      (n: unknown) =>
        typeof n === "number" && Number.isFinite(n) && n >= 0 && n <= 1,
    )
  );
};
/* One native decode at a time. The low-rate overview is for navigation, never fed into audio playback. */
const envelope = async (blob: Blob): Promise<Envelope> => {
  const audio = await new OfflineAudioContext(1, 1, sampleRate).decodeAudioData(
    await blob.arrayBuffer(),
  );
  const peaks = Array.from({ length: bins }, () => 0);
  Array.from({ length: audio.numberOfChannels }, (_, channel) =>
    audio.getChannelData(channel),
  ).forEach((samples) => {
    samples.forEach((sample, index) => {
      const bin = Math.min(
        bins - 1,
        Math.floor((index * bins) / samples.length),
      );
      peaks[bin] = Math.max(peaks[bin] ?? 0, Math.min(1, Math.abs(sample)));
    });
  });
  return { duration: audio.duration, peaks };
};
export const waveform = (resolve: LocalAssetResolver) => {
  const status = element("#waveform-status", HTMLElement);
  const paths = document.querySelectorAll<SVGPathElement>(".waveform-path");
  const cache = new Map<string, Envelope>();
  const state = { request: new AbortController(), queue: Promise.resolve() };
  const show = (data: Envelope | undefined) => {
    const peak = Math.max(0.01, ...(data?.peaks ?? []));
    const d =
      data?.peaks
        .map((value, i) => {
          const height = Math.max(0.6, (value / peak) * 21);
          return `M${String(i * 3 + 1)} ${String(24 - height)}V${String(24 + height)}`;
        })
        .join(" ") ?? "";
    paths.forEach((path) => {
      path.setAttribute("d", d);
    });
  };
  const select = (assetId: string, checksum: string): void => {
    state.request.abort();
    const request = new AbortController();
    state.request = request;
    show(undefined);
    status.textContent = "波形を準備中…";
    const cached = cache.get(checksum);
    /* Queueing prevents rapid selection from keeping several decoded audio buffers alive. */
    const generate = async () => {
      request.signal.throwIfAborted();
      try {
        const root = await navigator.storage.getDirectory();
        const directory = await root.getDirectoryHandle(
          "waveform-overviews-v1",
          { create: true },
        );
        const name = `${checksum}.json`;
        const stored: unknown = await directory
          .getFileHandle(name)
          .then((file) => file.getFile())
          .then((file) => file.text())
          .then((text) => JSON.parse(text) as unknown)
          .catch(() => undefined);
        request.signal.throwIfAborted();
        const data = valid(stored)
          ? stored
          : await envelope(await resolve(assetId, request.signal));
        request.signal.throwIfAborted();
        cache.set(checksum, data);
        await (valid(stored)
          ? Promise.resolve()
          : directory
              .getFileHandle(name, { create: true })
              .then((file) => file.createWritable())
              .then(async (writer) => {
                await writer.write(JSON.stringify(data));
                await writer.close();
              })
              .catch(() => undefined));
        request.signal.throwIfAborted();
        show(data);
        status.textContent = "";
      } catch {
        when(request.signal.aborted ? false : true, () => {
          status.textContent =
            "波形を表示できません。再生位置はこのまま選べます。";
        });
      }
    };
    (cached !== undefined
      ? () => {
          show(cached);
          status.textContent = "";
        }
      : () => {
          state.queue = state.queue.then(generate).catch(() => undefined);
        })();
  };
  window.addEventListener(
    "pagehide",
    () => {
      state.request.abort();
      cache.clear();
    },
    { once: true },
  );
  return { select };
};
