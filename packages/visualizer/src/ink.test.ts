import { expect, it } from "vitest";
import { restingFrame, restingMusic, sceneInk } from "./index";

it("keeps every ink readable on ivory, including simultaneous musical extremes", () => {
  const luminance = (rgb: number[]) =>
    rgb
      .map((c) => c / 255)
      .map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
      .reduce((sum, c, i) => sum + c * ([0.2126, 0.7152, 0.0722][i] ?? 0), 0);
  for (const pitch of [-1, 0, 1]) {
    for (const harmonyPhase of Array.from({ length: 13 }, (_, i) => i / 12)) {
      for (const third of [3 / 12, 4 / 12]) {
        const color = sceneInk({
          ...restingFrame,
          impulse: 1,
          music: {
            ...restingMusic,
            pitch,
            voiced: 1,
            harmonyPhase,
            harmonyActive: 1,
            third,
          },
        });
        const rgb = (color.match(/\d+/g) ?? []).map(Number);
        expect(rgb).toHaveLength(3);
        expect(
          (luminance([249, 245, 235]) + 0.05) / (luminance(rgb) + 0.05),
        ).toBeGreaterThan(7);
      }
    }
  }
});

it("follows harmony without flickering on melody or attacks", () => {
  const neutral = sceneInk(restingFrame);
  expect(sceneInk({ ...restingFrame, impulse: 1 })).toBe(neutral);
  expect(
    sceneInk({
      ...restingFrame,
      music: { ...restingMusic, pitch: 1, voiced: 1 },
    }),
  ).toBe(neutral);
  expect(
    sceneInk({
      ...restingFrame,
      music: { ...restingMusic, harmonyPhase: 0.5, harmonyActive: 1 },
    }),
  ).not.toBe(neutral);
});
