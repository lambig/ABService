/* eslint-disable functional/immutable-data -- DOM描画とイベント登録をデモ境界に閉じ、プレイヤーのsnapshotを表示する。 */
import { parseManifest } from "abservice-installation";
import { createPlayer } from "./index";
import type { LocalAssetResolver, PlayerSnapshot } from "./index";
import { manifest } from "./fixture";
import "./style.css";

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
const tracks = manifest.albums.flatMap((album) =>
  album.tracks.map((track) => ({ album, track })),
);
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
const render = (state: PlayerSnapshot): void => {
  const selection = tracks.find(
    (entry) => entry.track.trackId === state.trackId,
  );
  element("#track-title", HTMLElement).textContent =
    selection?.track.title ?? "曲を選んでください";
  element("#album-title", HTMLElement).textContent =
    selection?.album.title ?? "YOUR SELECTION";
  status.textContent = phases[state.phase];
  play.disabled = ["idle", "loading", "error", "playing"].includes(state.phase);
  pause.disabled = state.phase !== "playing";
  stop.disabled = state.phase === "idle";
  retry.hidden =
    state.trackId === null
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
    .querySelectorAll<HTMLButtonElement>("[data-track-id]")
    .forEach((button) => {
      button.setAttribute(
        "aria-pressed",
        String(button.dataset["trackId"] === state.trackId),
      );
    });
};
const paths: Readonly<Record<string, string>> = {
  "tone-first": "/fixtures/first.flac",
  "tone-second": "/fixtures/second.flac",
};
const resolve: LocalAssetResolver = async (assetId, signal) => {
  const path = paths[assetId];
  const response =
    path === undefined
      ? await Promise.reject(new Error("音源の対応がありません。"))
      : await fetch(path, { signal });
  return response.ok
    ? response.blob()
    : Promise.reject(new Error(`HTTP ${String(response.status)}`));
};
const parsed = parseManifest(manifest);
const start = (): void => {
  const contract =
    parsed.kind === "manifest"
      ? parsed.manifest
      : (() => {
          throw new Error("Invalid fixture manifest");
        })();
  const controller = createPlayer(contract, resolve, render);
  contract.albums.forEach((album, index) => {
    const section = document.createElement("section");
    const heading = document.createElement("h2");
    heading.textContent = album.title;
    const subtitle = document.createElement("p");
    subtitle.className = "album-meta";
    subtitle.textContent = `ALBUM ${String(index + 1).padStart(2, "0")} · ${String(album.tracks.length)} TRACKS`;
    const list = document.createElement("ol");
    album.tracks.forEach((track) => {
      const row = document.createElement("li");
      const button = document.createElement("button");
      button.textContent = track.title;
      button.dataset["trackId"] = track.trackId;
      button.setAttribute("aria-pressed", "false");
      button.addEventListener("click", () => {
        void controller.select(track.trackId);
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
    const trackId = controller.snapshot().trackId;
    void (trackId === null ? undefined : controller.select(trackId));
  });
  seek.addEventListener("input", () => {
    controller.seek(Number(seek.value));
  });
  window.addEventListener("pagehide", controller.dispose, { once: true });
  render(controller.snapshot());
};
start();
