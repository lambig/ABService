import type { PresentationFrame } from "./index";
import { restingMusic } from "./music";

/** Dark, readable inks shared by the scene and native UI. No hue leaves the warm palette. */
export const sceneInk = (frame: PresentationFrame): string => {
  const music = frame.music ?? restingMusic;
  const warmth = Math.min(
    1,
    Math.max(
      0,
      0.2 +
        music.harmonyPhase * music.harmonyActive * 0.35 +
        (music.pitch + 1) * music.voiced * 0.15 +
        frame.impulse * 0.15,
    ),
  );
  const channel = (black: number, rust: number) =>
    Math.round(black + (rust - black) * warmth);
  return `rgb(${String(channel(48, 116))} ${String(channel(43, 53))} ${String(channel(38, 36))})`;
};
