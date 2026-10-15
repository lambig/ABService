import type { PresentationFrame } from "./index";
import { restingMusic } from "./music";

/* C through B travel through twelve hues; dark inks retain readable UI contrast on ivory. */
const palette: readonly (readonly number[])[] = [
  [145, 32, 45],
  [128, 39, 87],
  [99, 46, 124],
  [66, 53, 137],
  [34, 65, 126],
  [19, 77, 105],
  [12, 86, 76],
  [29, 86, 43],
  [66, 78, 18],
  [104, 67, 12],
  [128, 49, 15],
  [141, 35, 28],
];

/** Root alone selects the pitch-class color; chord type controls motion separately. */
export const inkChannels = (frame: PresentationFrame): readonly number[] => {
  const music = frame.music ?? restingMusic;
  const index = ((Math.round(music.harmonyRoot) % 12) + 12) % 12;
  const color =
    music.harmonyActive > 0 ? (palette[index] ?? palette[0]) : [48, 43, 38];
  return color ?? [48, 43, 38];
};

/** The whole trail and selected UI share the current chord's ink, with no pitch/onset flicker. */
export const sceneInk = (frame: PresentationFrame): string =>
  `rgb(${inkChannels(frame).map(String).join(" ")})`;
