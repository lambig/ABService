import { describe, expect, it } from "vitest";
import { musicalMoment, parseMusicalAnalysis } from "./musical-analysis";

const fixture = () => ({
  schemaVersion: 1,
  audioSha256: "a".repeat(64),
  durationSeconds: 8,
  analysisVersion: "b".repeat(64),
  engines: { melody: "c".repeat(64), harmony: "d".repeat(64) },
  melody: [
    { start: 0, end: 1, midi: 72.2, salience: 0.7 },
    { start: 3, end: 5, midi: 79, salience: 0.5 },
  ],
  harmony: [{ start: 0.5, end: 4, root: 2, quality: "sus4", bass: 9 }],
});
describe("musical analysis contract", () => {
  it("preserves fractional melody and inversion; seek, silence, end and loop use media time", () => {
    const parsed = parseMusicalAnalysis(fixture());
    expect(parsed).toBeDefined();
    const checked =
      parsed ??
      (() => {
        throw Error("fixture");
      })();
    expect(musicalMoment(checked, 0.5).melody?.midi).toBe(72.2);
    expect(musicalMoment(checked, 3.5).harmony).toMatchObject({
      root: 2,
      quality: "sus4",
      bass: 9,
    });
    expect(musicalMoment(checked, 1).melody).toBeNull();
    expect(musicalMoment(checked, 5).melody).toBeNull();
    expect(musicalMoment(checked, 8).harmony).toBeNull();
    expect(musicalMoment(checked, 0).melody?.midi).toBe(72.2);
    expect(musicalMoment(checked, Number.NaN).melody).toBeNull();
  });
  it("rejects overlap, backwards time, nonfinite pitch, wrong schema and unknown properties", () => {
    const source = fixture();
    for (const value of [
      { ...source, schemaVersion: 2 },
      { ...source, extra: true },
      { ...source, audioSha256: "../invalid" },
      { ...source, durationSeconds: 4 },
      { ...source, melody: [{ start: 2, end: 1, midi: 60, salience: 1 }] },
      {
        ...source,
        melody: [{ start: 0, end: 2, midi: Infinity, salience: 1 }],
      },
      { ...source, melody: [...source.melody, source.melody[0]] },
    ])
      expect(parseMusicalAnalysis(value)).toBeUndefined();
  });
  it("allows unknown throughout and owns a snapshot independent of input", () => {
    const source = fixture();
    const parsed = parseMusicalAnalysis(source);
    expect(parsed?.melody).not.toBe(source.melody);
    expect(Object.isFrozen(parsed?.melody[0])).toBe(true);
    expect(
      parseMusicalAnalysis({ ...source, melody: [], harmony: [] }),
    ).toBeDefined();
  });
});
