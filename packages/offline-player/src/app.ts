/* eslint-disable functional/immutable-data -- DOM描画とイベント登録をデモ境界に閉じ、プレイヤーのsnapshotを表示する。 */
import "./runtime-policy";
import type { InstallationManifest } from "abservice-installation";
import {
  artworkAssetIds,
  toPresentationData,
} from "abservice-listening-presentation";
import type { PresentationData } from "abservice-listening-presentation";
import { createAssetStore } from "abservice-offline-storage";
import { createPlayer, settledPhase } from "abservice-player";
import type {
  LocalAssetResolver,
  PlayerSnapshot,
  SettledPhase,
} from "abservice-player";
import "player-study/style.css";
import "./style.css";
import { preparation, storageMessage } from "./preparation";
import { connectMediaAnalysis } from "abservice-audio-worklet/media";
import { probeRequested } from "abservice-visualizer";
import { benchmarkAudio, createAudioProbe } from "./audio-probe";
import { measured, presentation, probeReport } from "./presentation";

/* Startup cost is measured from navigation start: script evaluated, library ready, first frame drawn. */
performance.mark("listening:script");

const element = <T extends HTMLElement>(
  selector: string,
  type: { new (): T },
): T => {
  const node = document.querySelector<T>(selector);
  return node instanceof type
    ? node
    : (() => {
        throw new Error(`Missing ${selector}`);
      })();
};
const play = element("#play", HTMLButtonElement);
const pause = element("#pause", HTMLButtonElement);
const stop = element("#stop", HTMLButtonElement);
const retry = element("#retry", HTMLButtonElement);
const seek = element("#seek", HTMLInputElement);
const status = element("#play-status", HTMLElement);
const visual = presentation(
  element("#visualizer", HTMLCanvasElement),
  element("#visualizer-status", HTMLElement),
);
const analysisStatus = element("#analysis-status", HTMLElement);
/* Audio-side observation follows the same hash as the frame probe. */
const audioProbe = probeRequested(location.hash)
  ? createAudioProbe()
  : undefined;
/* The device rate is known only once playback has created the AudioContext; before that the common rate is assumed. */
const fallbackSampleRate = 48000;
/*
 * The benchmark and real playback are separate pieces of evidence: running both at once would let the worker and the
 * decode load the Worklet and inflate its load and underruns. The benchmark waits for playback to stop, and playback
 * waits for the benchmark.
 */
const benchmarkBlocked: readonly SettledPhase[] = ["loading", "playing"];
const benchmarkAllowed = (phase: SettledPhase): boolean =>
  benchmarkBlocked.every((blocked) => blocked !== phase);
const phases: Record<SettledPhase, string> = {
  idle: "待機中",
  loading: "読み込み中",
  ready: "再生できます",
  playing: "再生中",
  paused: "一時停止",
  ended: "再生終了",
  error: "読み込みに失敗しました",
};
const time = (seconds: number): string =>
  `${String(Math.floor(seconds / 60))}:${String(Math.floor(seconds % 60)).padStart(2, "0")}`;
/* Everything shown comes from the presentation data; the Manifest stays with the player and the store. */
const render = (
  data: PresentationData | undefined,
  state: PlayerSnapshot,
): void => {
  const selection = data?.playbackItems.find(
    (entry) => entry.playbackItemId === state.playbackItemId,
  );
  element("#track-title", HTMLElement).textContent =
    selection?.title ?? "音源を選んでください";
  element("#album-title", HTMLElement).textContent =
    data?.albums.find((album) => album.albumId === selection?.albumId)?.title ??
    "YOUR SELECTION";
  /* A seek is shown as where it returns to; the presentation state follows the seek itself. */
  const phase = settledPhase(state);
  status.textContent = phases[phase];
  const benchmarking = audioProbe?.benchmarking() === true;
  play.disabled = [
    benchmarking,
    ["idle", "loading", "error", "playing"].includes(phase),
  ].some(Boolean);
  element("#probe-benchmark", HTMLButtonElement).disabled = [
    benchmarking,
    benchmarkBlocked.includes(phase),
  ].some(Boolean);
  pause.disabled = phase !== "playing";
  stop.disabled = phase === "idle";
  retry.hidden =
    state.playbackItemId === null
      ? true
      : ["idle", "error"].includes(phase)
        ? false
        : true;
  seek.disabled = state.duration === 0;
  seek.max = String(state.duration);
  seek.value = String(state.position);
  element("#position", HTMLElement).textContent = time(state.position);
  element("#duration", HTMLElement).textContent = time(state.duration);
  element("#error", HTMLElement).textContent = state.error ?? "";
  document
    .querySelectorAll<HTMLButtonElement>("[data-playback-item-id]")
    .forEach((button) => {
      button.setAttribute(
        "aria-pressed",
        String(button.dataset["playbackItemId"] === state.playbackItemId),
      );
    });
};
const idle: PlayerSnapshot = {
  phase: "idle",
  playbackItemId: null,
  trackId: null,
  position: 0,
  duration: 0,
  error: null,
};
/* The player reads only the verified bytes of the active package; playing never downloads. */
const resolverFor =
  (contract: InstallationManifest): LocalAssetResolver =>
  async (assetId, signal) => {
    const opened = createAssetStore(contract);
    const result =
      opened.kind === "ok" ? await opened.value.read(assetId, signal) : opened;
    return result.kind === "ok"
      ? result.value
      : Promise.reject(new Error(storageMessage(result.error)));
  };
/* Only verified artwork bytes are shown; one that fails verification is left out, and the view omits it. */
const presentationOf = async (
  contract: InstallationManifest,
): Promise<PresentationData> => {
  const opened = createAssetStore(contract);
  const signal = new AbortController().signal;
  const read = await Promise.all(
    artworkAssetIds(contract).map(async (assetId) => {
      const result =
        opened.kind === "ok"
          ? await opened.value.read(assetId, signal)
          : opened;
      return result.kind === "ok" ? [[assetId, result.value] as const] : [];
    }),
  );
  return toPresentationData(contract, new Map(read.flat()));
};
const shown: { albumId: string | undefined; url: string | undefined } = {
  albumId: undefined,
  url: undefined,
};
const reveal = (node: HTMLElement, visible: boolean): void => {
  node.hidden = visible ? false : true;
};
/* Each present fact gets its own region; an absent one hides its region instead of showing a placeholder. */
const showAlbum = (
  data: PresentationData,
  albumId: string | undefined,
): void => {
  const album = data.albums.find((entry) => entry.albumId === albumId);
  const artwork = element("#artwork", HTMLImageElement);
  const artist = element("#artist", HTMLElement);
  const description = element("#description", HTMLElement);
  const replace = (): void => {
    (shown.url === undefined
      ? () => undefined
      : () => {
          URL.revokeObjectURL(shown.url as string);
        })();
    shown.albumId = albumId;
    shown.url =
      album?.artwork === undefined
        ? undefined
        : URL.createObjectURL(album.artwork);
    reveal(element("#album-info", HTMLElement), album !== undefined);
    artwork.src = shown.url ?? "";
    reveal(artwork, shown.url !== undefined);
    artist.textContent = album?.artistDisplayName ?? "";
    reveal(artist, album?.artistDisplayName !== undefined);
    /* Sanitised by the shared markup renderer, with images and links already removed. */
    description.innerHTML = album?.descriptionHtml ?? "";
    reveal(description, album?.descriptionHtml !== undefined);
  };
  (albumId === shown.albumId ? () => undefined : replace)();
};
const open = (contract: InstallationManifest, data: PresentationData): void => {
  const items = data.playbackItems;
  const controller = createPlayer(
    contract,
    resolverFor(contract),
    /* The DOM updates for a player change count towards the next frame's cycle when probing. */
    measured((state: PlayerSnapshot) => {
      render(data, state);
      showAlbum(
        data,
        items.find((item) => item.playbackItemId === state.playbackItemId)
          ?.albumId,
      );
      visual.sync(
        state,
        data.albums.find(
          (album) =>
            album.albumId ===
            items.find((item) => item.playbackItemId === state.playbackItemId)
              ?.albumId,
        ),
      );
      (state.phase === "error" ? preparation.invalidate : () => undefined)();
    }),
    (media, signal) => {
      analysisStatus.textContent = "";
      /* Watching starts before connecting so that the last playback stats are copied before the connection is dropped. */
      const attach = audioProbe?.watch(signal);
      const connection = connectMediaAnalysis(media, signal, {
        onFeatures:
          audioProbe === undefined
            ? visual.update
            : (features) => {
                audioProbe.features(features);
                visual.update(features);
              },
        onReset:
          audioProbe === undefined
            ? visual.reset
            : () => {
                audioProbe.reset();
                visual.reset();
              },
        onError: () => {
          analysisStatus.textContent =
            "音響解析を利用できません。再生と操作は続けられます。";
        },
        ...(audioProbe === undefined ? {} : { onLoad: audioProbe.load }),
      });
      attach?.(connection);
      return connection;
    },
  );
  /* The benchmark decodes on the main thread, so it runs only when asked and never by itself. */
  const runBenchmark = async (): Promise<void> => {
    const probe = audioProbe;
    const selected = controller.snapshot().playbackItemId;
    const item =
      items.find((entry) => entry.playbackItemId === selected) ?? items[0];
    const measure = async (): Promise<void> => {
      probe?.benchmark({ kind: "running" });
      render(data, controller.snapshot());
      report();
      try {
        const audio = await resolverFor(contract)(
          (item as (typeof items)[number]).audioAssetId,
          new AbortController().signal,
        );
        const outcome = await benchmarkAudio(
          audio,
          probe?.sampleRate() ?? fallbackSampleRate,
        );
        probe?.benchmark({ kind: "done", ...outcome });
      } catch (error) {
        probe?.benchmark({ kind: "failed", message: String(error) });
      }
      render(data, controller.snapshot());
      report();
    };
    const ready = [
      probe !== undefined,
      probe?.benchmarking() === false,
      item !== undefined,
      benchmarkAllowed(settledPhase(controller.snapshot())),
    ].every(Boolean);
    await (ready ? measure() : Promise.resolve());
  };
  element("#probe-benchmark", HTMLButtonElement).addEventListener(
    "click",
    () => {
      void runBenchmark();
    },
  );
  data.albums.forEach((album, index) => {
    const section = document.createElement("section");
    const heading = document.createElement("h2");
    heading.textContent = album.title;
    const subtitle = document.createElement("p");
    subtitle.className = "album-meta";
    subtitle.textContent = `ALBUM ${String(index + 1).padStart(2, "0")} · ${String(album.tracks.length)} TRACKS`;
    const list = document.createElement("ol");
    items
      .filter((item) => item.albumId === album.albumId)
      .forEach((item) => {
        const row = document.createElement("li");
        const button = document.createElement("button");
        button.textContent = item.title;
        button.dataset["playbackItemId"] = item.playbackItemId;
        button.setAttribute("aria-pressed", "false");
        button.addEventListener("click", () => {
          void controller.select(item.playbackItemId);
        });
        row.append(button);
        list.append(row);
      });
    section.append(subtitle, heading, list);
    element("#library", HTMLElement).append(section);
  });
  play.addEventListener("click", () => {
    void (audioProbe?.benchmarking() === true ? undefined : controller.play());
  });
  pause.addEventListener("click", controller.pause);
  stop.addEventListener("click", controller.stop);
  retry.addEventListener("click", () => {
    const playbackItemId = controller.snapshot().playbackItemId;
    void (playbackItemId === null
      ? undefined
      : controller.select(playbackItemId));
  });
  seek.addEventListener("input", () => {
    controller.seek(Number(seek.value));
  });
  window.addEventListener("pagehide", controller.dispose, { once: true });
  render(data, controller.snapshot());
};
const start = async (): Promise<void> => {
  window.addEventListener("pagehide", visual.dispose, { once: true });
  render(undefined, idle);
  const active = await preparation.mount();
  const data = active === undefined ? undefined : await presentationOf(active);
  (active !== undefined && data !== undefined
    ? () => {
        open(active, data);
      }
    : () => undefined)();
  performance.mark("listening:ready");
};
/* The probe overlay is refreshed once a second, only when the hash asks for it. */
function report(): void {
  const frames = probeReport();
  const text =
    frames === undefined
      ? undefined
      : [frames, ...(audioProbe?.report() ?? [])].join("\n");
  const overlay = element("#probe", HTMLElement);
  overlay.hidden = text === undefined;
  overlay.textContent = text ?? "";
  element("#probe-benchmark", HTMLButtonElement).hidden = text === undefined;
}
(probeReport() === undefined
  ? () => undefined
  : () => {
      report();
      const timer = setInterval(report, 1000);
      window.addEventListener(
        "pagehide",
        () => {
          clearInterval(timer);
        },
        { once: true },
      );
    })();
void start();
