/* eslint-disable functional/immutable-data -- DOM描画とイベント登録をデモ境界に閉じ、プレイヤーのsnapshotを表示する。 */
import { getPlaybackItems } from "abservice-installation";
import type { InstallationManifest } from "abservice-installation";
import { createAssetStore } from "abservice-offline-storage";
import { createPlayer } from "abservice-player";
import type { LocalAssetResolver, PlayerSnapshot } from "abservice-player";
import "player-study/style.css";
import "./style.css";
import { preparation, storageMessage } from "./preparation";
import { connectMediaAnalysis } from "abservice-audio-worklet/media";
import { presentation } from "./presentation";

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
const phases: Record<PlayerSnapshot["phase"], string> = {
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
const render = (
  manifest: InstallationManifest | undefined,
  state: PlayerSnapshot,
): void => {
  const items = manifest === undefined ? [] : getPlaybackItems(manifest);
  const selection = items.find(
    (entry) => entry.playbackItemId === state.playbackItemId,
  );
  element("#track-title", HTMLElement).textContent =
    selection?.title ?? "音源を選んでください";
  element("#album-title", HTMLElement).textContent =
    manifest?.albums.find((album) => album.albumId === selection?.albumId)
      ?.title ?? "YOUR SELECTION";
  status.textContent = phases[state.phase];
  play.disabled = ["idle", "loading", "error", "playing"].includes(state.phase);
  pause.disabled = state.phase !== "playing";
  stop.disabled = state.phase === "idle";
  retry.hidden =
    state.playbackItemId === null
      ? true
      : ["idle", "error"].includes(state.phase)
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
const open = (contract: InstallationManifest): void => {
  const items = getPlaybackItems(contract);
  const controller = createPlayer(
    contract,
    resolverFor(contract),
    (state) => {
      render(contract, state);
      visual.sync(state);
      (state.phase === "error" ? preparation.invalidate : () => undefined)();
    },
    (media, signal) => {
      analysisStatus.textContent = "";
      return connectMediaAnalysis(media, signal, {
        onFeatures: visual.update,
        onReset: visual.reset,
        onError: () => {
          analysisStatus.textContent =
            "音響解析を利用できません。再生と操作は続けられます。";
        },
      });
    },
  );
  contract.albums.forEach((album, index) => {
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
    void controller.play();
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
  render(contract, controller.snapshot());
};
const start = async (): Promise<void> => {
  window.addEventListener("pagehide", visual.dispose, { once: true });
  render(undefined, idle);
  const active = await preparation.mount();
  (active === undefined
    ? () => undefined
    : () => {
        open(active);
      })();
};
void start();
