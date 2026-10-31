/* eslint-disable functional/immutable-data -- DOM/cache updates are isolated here. */
import { element, when } from "./dom";

type Envelope = Readonly<{ duration: number; peaks: readonly number[] }>;
const bins = 256;
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
export const waveform = () => {
  const status = element("#waveform-status", HTMLElement);
  const paths = document.querySelectorAll<SVGPathElement>(".waveform-path");
  const cache = new Map<string, Envelope>();
  const state = { request: new AbortController() };
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
  const select = (checksum: string): void => {
    state.request.abort();
    const request = new AbortController();
    state.request = request;
    show(undefined);
    status.textContent = "再生位置をタップで選べます";
    const cached = cache.get(checksum);
    /* Selection only reads the small saved overview, never an audio asset or its lock. */
    const load = async () => {
      request.signal.throwIfAborted();
      try {
        const root = await navigator.storage.getDirectory();
        const directory = await root.getDirectoryHandle(
          "waveform-overviews-v1",
        );
        const name = `${checksum}.json`;
        const stored: unknown = await directory
          .getFileHandle(name)
          .then((file) => file.getFile())
          .then((file) => (file.size <= 16384 ? file.text() : "null"))
          .then((text) => JSON.parse(text) as unknown)
          .catch(() => undefined);
        request.signal.throwIfAborted();
        const data = valid(stored) ? stored : undefined;
        when(data !== undefined, () => {
          cache.set(checksum, data as Envelope);
        });
        show(data);
        status.textContent =
          data === undefined ? "再生位置をタップで選べます" : "";
      } catch {
        when(request.signal.aborted ? false : true, () => {
          status.textContent = "再生位置をタップで選べます";
        });
      }
    };
    (cached !== undefined
      ? () => {
          show(cached);
          status.textContent = "";
        }
      : () => {
          void load().catch(() => undefined);
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
