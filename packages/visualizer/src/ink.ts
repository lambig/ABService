import type { PresentationFrame } from "./index";
import { restingMusic } from "./music";

/* Alternating warm inks make common fifth-related changes visible on ivory. */
const palette: readonly (readonly number[])[] = [
  [138, 44, 28],
  [38, 34, 31],
  [111, 72, 26],
  [109, 35, 51],
  [65, 48, 32],
  [140, 47, 31],
  [43, 37, 33],
  [115, 68, 28],
  [112, 36, 47],
  [60, 46, 33],
  [133, 49, 27],
  [46, 35, 33],
];

/** Dark, readable inks shared by the scene and native UI. No hue leaves the warm palette. */
export const inkChannels = (frame: PresentationFrame): readonly number[] => {
  const music = frame.music ?? restingMusic;
  const index = ((Math.round(music.harmonyPhase * 12) % 12) + 12) % 12;
  const color =
    music.harmonyActive > 0 ? (palette[index] ?? palette[0]) : [48, 43, 38];
  const shade = music.third === 3 / 12 ? 0.82 : 1;
  return (color ?? [48, 43, 38]).map((channel) => Math.round(channel * shade));
};

/** The whole trail and selected UI share the current chord's ink, with no pitch/onset flicker. */
export const sceneInk = (frame: PresentationFrame): string =>
  `rgb(${inkChannels(frame).map(String).join(" ")})`;
