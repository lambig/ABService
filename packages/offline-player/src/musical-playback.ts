/* eslint-disable functional/immutable-data -- Owns the selected interpretation and one media-clock animation, cancelled with the playback session. */
import { getPlaybackItems, musicalMoment } from "abservice-installation";
import type {
  InstallationManifest,
  MusicalAnalysis,
  MusicalMoment,
} from "abservice-installation";
import { createAnalysisStore } from "abservice-offline-storage";
import type { PlayerSnapshot } from "abservice-player";
import { when } from "./dom";

export type MusicalPlayback = Readonly<{
  status: "missing" | "loading" | "ready" | "invalid" | "inactive";
  playbackItemId: string | null;
  analysisVersion: string | null;
  moment: MusicalMoment | null;
}>;

export const musicalPlayback = (
  manifest: InstallationManifest,
  changed: (state: MusicalPlayback) => void,
) => {
  const store = createAnalysisStore(manifest);
  const cell: {
    item: string | null;
    request: AbortController;
    data: MusicalAnalysis | undefined;
    status: MusicalPlayback["status"];
    media: HTMLMediaElement | null;
    frame: number | null;
    phase: PlayerSnapshot["phase"];
  } = {
    item: null,
    request: new AbortController(),
    data: undefined,
    status: "missing",
    media: null,
    frame: null,
    phase: "idle",
  };
  const publish = (): void => {
    const media = cell.media;
    const data = cell.data;
    const mismatch =
      media !== null &&
      data !== undefined &&
      Number.isFinite(media.duration) &&
      Math.abs(media.duration - data.durationSeconds) > 0.05;
    const active =
      media !== null &&
      ["ready", "playing", "paused", "seeking"].includes(cell.phase);
    const status = mismatch ? "invalid" : active ? cell.status : "inactive";
    changed(
      Object.freeze({
        status,
        playbackItemId: cell.item,
        analysisVersion: data?.analysisVersion ?? null,
        moment:
          active && (mismatch ? false : true) && data !== undefined
            ? musicalMoment(data, media.currentTime)
            : null,
      }),
    );
  };
  const cancel = (): void => {
    when(cell.frame !== null, () => {
      cancelAnimationFrame(cell.frame as number);
    });
    cell.frame = null;
  };
  const tick = (): void => {
    cell.frame = null;
    publish();
    when(
      cell.media !== null &&
        (cell.media.paused ? false : true) &&
        (cell.media.ended ? false : true),
      () => {
        cell.frame = requestAnimationFrame(tick);
      },
    );
  };
  const refresh = async (): Promise<void> => {
    cell.request.abort();
    const request = new AbortController();
    cell.request = request;
    cell.data = undefined;
    cell.status = "loading";
    publish();
    const item = getPlaybackItems(manifest).find(
      (item) => item.playbackItemId === cell.item,
    );
    const asset = manifest.assets.find(
      (asset) => asset.assetId === item?.audioAssetId,
    );
    const result =
      asset === undefined
        ? { kind: "ok" as const, value: undefined }
        : asset.checksum.algorithm === "sha256"
          ? await store.read(asset.checksum.value, request.signal)
          : { kind: "ok" as const, value: undefined };
    when(request.signal.aborted ? false : true, () => {
      cell.data = result.kind === "ok" ? result.value : undefined;
      cell.status =
        result.kind === "error"
          ? "invalid"
          : result.value === undefined
            ? "missing"
            : "ready";
      publish();
    });
  };
  return Object.freeze({
    sync: (state: PlayerSnapshot): void => {
      cell.phase = state.phase;
      (cell.item !== state.playbackItemId
        ? () => {
            cell.item = state.playbackItemId;
            void refresh();
          }
        : publish)();
    },
    attach: (media: HTMLMediaElement, signal: AbortSignal): void => {
      cancel();
      cell.media = media;
      const update = (): void => {
        cancel();
        tick();
      };
      for (const event of [
        "playing",
        "pause",
        "seeking",
        "seeked",
        "timeupdate",
        "ended",
        "loadedmetadata",
      ]) {
        media.addEventListener(event, update, { signal });
      }
      signal.addEventListener(
        "abort",
        () => {
          when(cell.media === media, () => {
            cancel();
            cell.media = null;
            publish();
          });
        },
        { once: true },
      );
    },
    refresh,
    dispose: (): void => {
      cell.request.abort();
      cancel();
      cell.media = null;
      cell.data = undefined;
      publish();
    },
  });
};
