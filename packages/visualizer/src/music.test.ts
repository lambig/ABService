import { describe, expect, it } from "vitest";
import { mapMusicalExpression, restingMusic } from "./music";

describe("musical expression", () => {
  it("keeps harmony visible through a melody gap", () => {
    const harmony = { root: 2, quality: "sus4" };
    const note = mapMusicalExpression({
      position: 0,
      melody: { midi: 72 },
      harmony,
    });
    const gap = mapMusicalExpression({ position: 1, melody: null, harmony });
    expect(note.voiced).toBe(1);
    expect(gap.voiced).toBe(0);
    expect(gap.pitch).toBe(0);
    expect(gap.harmonyPhase).toBe(note.harmonyPhase);
    expect(gap.harmonyActive).toBe(1);
  });

  it("distinguishes pitch, chord root and chord quality independently", () => {
    const input = {
      position: 0,
      melody: { midi: 60 },
      harmony: { root: 0, quality: "major" },
    };
    const base = mapMusicalExpression(input);
    const high = mapMusicalExpression({ ...input, melody: { midi: 84 } });
    const root = mapMusicalExpression({
      ...input,
      harmony: { root: 7, quality: "major" },
    });
    const minor = mapMusicalExpression({
      ...input,
      harmony: { root: 0, quality: "minor" },
    });
    expect(high.pitch).toBeGreaterThan(base.pitch);
    expect(high.harmonyPhase).toBe(base.harmonyPhase);
    expect(root.harmonyPhase).not.toBe(base.harmonyPhase);
    expect(minor.harmonyPhase).not.toBe(base.harmonyPhase);
    expect(root.pitch).toBe(base.pitch);
  });

  it("clears absent analysis and contains malformed input", () => {
    expect(mapMusicalExpression(null)).toEqual(restingMusic);
    expect(
      mapMusicalExpression({
        position: 1,
        melody: { midi: NaN },
        harmony: { root: 12, quality: "major" },
      }),
    ).toEqual(restingMusic);
    expect(
      mapMusicalExpression({
        position: 1,
        melody: { midi: 127 },
        harmony: null,
      }).pitch,
    ).toBe(1);
  });
});
