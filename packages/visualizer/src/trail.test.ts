import { expect, it } from "vitest";
import {
  advanceTrail,
  melodicStep,
  emptyTrail,
  trailHeadX,
  trailPosition,
  trailSegments,
} from "./trail";
import { flowOrnaments, flowVertices } from "./flow-geometry";
import { restingFrame } from "./index";

it("keeps the melody dot at the right and sends the previous pitches left", () => {
  const first = advanceTrail(emptyTrail, "a", 0, -0.2);
  const second = advanceTrail(first, "a", 0.1, 0.1);
  const third = advanceTrail(second, "a", 0.2, 0);
  const old = trailSegments(second)[0];
  const moved = trailSegments(third)[0];
  expect(old?.[1].x).toBe(trailHeadX);
  expect(old?.[1].y).toBeLessThan(old?.[0].y ?? 0);
  expect(moved?.[0].y).toBe(old?.[0].y);
  expect(moved?.[0].x).toBeLessThan(old?.[0].x ?? 0);
  expect(trailPosition({ time: 0.2, pitch: 0 }, 0.2).x).toBe(trailHeadX);
});

it("holds height through unvoiced intervals, but clears on seeks or source changes", () => {
  const samples = [0, 0.1, null, null, 0.5, 0.6];
  const trail = samples.reduce(
    (state, pitch, i) => advanceTrail(state, "a", i / 10, pitch),
    emptyTrail,
  );
  expect(trailSegments(trail)).toHaveLength(5);
  const stalled = advanceTrail(trail, "a", 1, 0.7);
  expect(trailSegments(stalled)).toHaveLength(5);
  expect(trailSegments(advanceTrail(trail, "a", 0.2, 0))).toEqual([]);
  expect(trailSegments(advanceTrail(trail, "b", 0.6, 0))).toEqual([]);
  expect(trailSegments(advanceTrail(emptyTrail, "a", 0.6, 0))).toEqual([]);
});

it("holds on pause and bounds both dense history and uploaded geometry", () => {
  const trail = Array.from({ length: 10000 }, (_, i) => i / 120).reduce(
    (state, time) => advanceTrail(state, "a", time, Math.sin(time)),
    emptyTrail,
  );
  expect(trail.samples.length).toBeLessThanOrEqual(400);
  expect(
    (trail.head?.time ?? 0) - (trail.samples[0]?.time ?? 0),
  ).toBeLessThanOrEqual(12);
  expect(
    advanceTrail(trail, "a", trail.head?.time ?? 0, trail.head?.pitch ?? null),
  ).toEqual(trail);
  const vertices = flowVertices({ ...restingFrame, trail }, 390, 844);
  expect(vertices.every(Number.isFinite)).toBe(true);
  expect(vertices.byteLength).toBeLessThanOrEqual(200000);
  expect(flowVertices({ ...restingFrame, trail }, 390, 844)).toEqual(vertices);
  expect(flowVertices(restingFrame, 390, 844)).toHaveLength(0);
});

it("moves ornaments left while rotating at stable random heights, including a birth boundary", () => {
  const before = flowOrnaments(7.1);
  const after = flowOrnaments(7.3);
  before
    .filter((a) => after.some((b) => a.id === b.id))
    .forEach((a) => {
      const b = after.find((value) => value.id === a.id);
      expect(b?.x).toBeLessThan(a.x);
      expect(b?.y).toBe(a.y);
      expect(b?.angle).toBeGreaterThan(a.angle);
    });
  expect(new Set(before.map((value) => value.y)).size).toBe(5);
  expect(flowOrnaments(7.1)).toEqual(before);
});

it("folds octave-like leaps while retaining ordinary melodic intervals", () => {
  for (const jump of [12, -12, 24, -24]) expect(melodicStep(jump)).toBe(0);
  expect(melodicStep(14)).toBe(2);
  expect(melodicStep(-14)).toBe(-2);
  for (const jump of [-7, -5, 3, 7, 9, 16])
    expect(melodicStep(jump)).toBe(jump);
  const first = advanceTrail(emptyTrail, "a", 0, (60 - 69) / 24);
  const octave = advanceTrail(first, "a", 0.1, (72 - 69) / 24);
  expect(octave.head?.pitch).toBe(first.head?.pitch);
  const silent = advanceTrail(octave, "a", 0.2, null);
  expect(silent.head?.pitch).toBe(octave.head?.pitch);
  const next = advanceTrail(silent, "a", 0.3, (74 - 69) / 24);
  expect(next.head?.pitch).toBeCloseTo((62 - 69) / 24);
  expect(advanceTrail(emptyTrail, "a", 0, null).head?.pitch).toBe(0);
});

it("keeps octave-unison rolls level and continues from that height when the melody moves", () => {
  const notes = [60, 72, 60, 72, 60, 72, 74, 62, 74, 64];
  const expected = [60, 60, 60, 60, 60, 60, 62, 62, 62, 64];
  notes.reduce((state, midi, i) => {
    const next = advanceTrail(state, "roll", i / 10, (midi - 69) / 24);
    expect(next.head?.pitch).toBeCloseTo(((expected[i] ?? 0) - 69) / 24);
    return next;
  }, emptyTrail);
});
