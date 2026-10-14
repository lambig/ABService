import type { PresentationFrame } from "./index";
import { restingMusic } from "./music";

/** Dark, readable inks shared by the scene and native UI. No hue leaves the warm palette. */
export const inkChannels = (frame: PresentationFrame): readonly number[] => {
  const music = frame.music ?? restingMusic;
  const warmth = Math.min(
    1,
    Math.max(0, 0.15 + music.harmonyPhase * music.harmonyActive * 0.85),
  );
  const channel = (black: number, rust: number) =>
    Math.round(black + (rust - black) * warmth);
  return [channel(48, 116), channel(43, 53), channel(38, 36)];
};

/** The whole trail and selected UI share the current chord's ink, with no pitch/onset flicker. */
export const sceneInk = (frame: PresentationFrame): string =>
  `rgb(${inkChannels(frame).map(String).join(" ")})`;
