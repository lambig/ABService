/* eslint-disable functional/immutable-data -- Bounded waveform reduction and DOM/cache updates are isolated here. */
import type { LocalAssetResolver } from "abservice-player";
import { element, when } from "./dom";

type Envelope = Readonly<{ duration: number; peaks: readonly number[] }>;
const bins = 256;
const sampleRate = 8000;
const maxBytes = 64 * 1024 * 1024;
const maxDuration = 600;
const maxSourcePcmBytes = 256 * 1024 * 1024;
/* RFC 9639 section 8.2: native FLAC starts with the 34-byte STREAMINFO.
 * Read only 42 bytes before allocating the encoded buffer or invoking decode.
 * Unknown lengths/formats degrade to the ordinary seek control. This is an
 * overview budget for validated assets, not a hostile-file decoder sandbox. */
const headerAllowed = (bytes: ArrayBuffer): boolean => {
  const view = new DataView(bytes);
  const info = view.getBigUint64(18);
  const rate = Number(info >> 44n);
  const channels = Number((info >> 41n) & 7n) + 1;
  const samples = Number(info & 0xfffffffffn);
  return (
    view.getUint32(0) === 0x664c6143 &&
    (view.getUint8(4) & 0x7f) === 0 &&
    (view.getUint32(4) & 0xffffff) === 34 &&
    rate > 0 &&
    channels <= 2 &&
    samples > 0 &&
    samples / rate <= maxDuration &&
    samples * channels * 4 <= maxSourcePcmBytes
  );
};
export const waveformInputAllowed = async (blob: Blob): Promise<boolean> =>
  blob.size >= 42 && blob.size <= maxBytes
    ? blob
        .slice(0, 42)
        .arrayBuffer()
        .then((bytes) =>
          bytes.byteLength === 42 ? headerAllowed(bytes) : false,
        )
    : false;
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
const requireBudget = (allowed: boolean): void => {
  (allowed
    ? () => undefined
    : () => {
        throw new Error("Overview budget");
      })();
};
/* One native decode at a time. The low-rate overview is for navigation, never fed into audio playback. */
const envelope = async (blob: Blob, signal: AbortSignal): Promise<Envelope> => {
  requireBudget(await waveformInputAllowed(blob));
  signal.throwIfAborted();
  const encoded = await blob.arrayBuffer();
  signal.throwIfAborted();
  const audio = await new OfflineAudioContext(1, 1, sampleRate).decodeAudioData(
    encoded,
  );
  signal.throwIfAborted();
  requireBudget(audio.duration <= maxDuration && audio.numberOfChannels <= 2);
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
          .then((file) => (file.size <= 16384 ? file.text() : "null"))
          .then((text) => JSON.parse(text) as unknown)
          .catch(() => undefined);
        request.signal.throwIfAborted();
        const data = valid(stored)
          ? stored
          : await envelope(
              await resolve(assetId, request.signal),
              request.signal,
            );
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
          status.textContent = "再生位置はこのまま選べます。";
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
