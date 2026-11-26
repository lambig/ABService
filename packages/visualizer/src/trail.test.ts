import { expect, it } from "vitest";
import {
  advanceTrail,
  noteHeight,
  emptyTrail,
  trailHeadX,
  trailPosition,
  trailSegments,
} from "./trail";
import { flowOrnaments, flowVertices } from "./flow-geometry";
import { restingFrame } from "./index";

it("anchors the quill nib at the current note and keeps its feather above the trail", () => {
  const trail = advanceTrail(emptyTrail, "a", 2, 72);
  const vertices = flowVertices({ ...restingFrame, trail }, 390, 844);
  const opaque = Array.from({ length: vertices.length / 6 }, (_, i) =>
    Array.from(vertices.slice(i * 6, i * 6 + 6)),
  ).filter((v) => v[5] === 1);
  const tip = trailPosition({ time: 2, pitch: noteHeight(72) }, 2);
  expect(opaque[0]?.[0]).toBeCloseTo(tip.x * 2 - 1);
  expect(opaque[0]?.[1]).toBeCloseTo(1 - tip.y * 2);
  expect(opaque.every((v) => (v[0] ?? 0) >= tip.x * 2 - 1 - 0.00001)).toBe(
    true,
  );
  expect(
    Math.max(...opaque.map((v) => v[1] ?? 0)) - (opaque[0]?.[1] ?? 0),
  ).toBeGreaterThan(60 / 844);
});

it("keeps the melody dot at the right and sends the previous pitches left", () => {
  const first = advanceTrail(emptyTrail, "a", 0, 60);
  const second = advanceTrail(first, "a", 0.1, 64);
  const third = advanceTrail(second, "a", 0.2, 62);
  const old = trailSegments(second)[0];
  const moved = trailSegments(third)[0];
  expect(old?.[1].x).toBe(trailHeadX);
  expect(old?.[1].y).toBeLessThan(old?.[0].y ?? 0);
  expect(moved?.[0].y).toBe(old?.[0].y);
  expect(moved?.[0].x).toBeLessThan(old?.[0].x ?? 0);
  expect(trailPosition({ time: 0.2, pitch: 0 }, 0.2).x).toBe(trailHeadX);
});

it("holds height through unvoiced intervals, but clears on seeks or source changes", () => {
  const samples = [60, 62, null, null, 64, 65];
  const trail = samples.reduce(
    (state, pitch, i) => advanceTrail(state, "a", i / 10, pitch),
    emptyTrail,
  );
  expect(trailSegments(trail)).toHaveLength(5);
  const stalled = advanceTrail(trail, "a", 1, 67);
  expect(trailSegments(stalled)).toHaveLength(5);
  expect(trailSegments(advanceTrail(trail, "a", 0.2, 0))).toEqual([]);
  expect(trailSegments(advanceTrail(trail, "b", 0.6, 0))).toEqual([]);
  expect(trailSegments(advanceTrail(emptyTrail, "a", 0.6, 0))).toEqual([]);
});

it("holds on pause and bounds both dense history and uploaded geometry", () => {
  const trail = Array.from({ length: 10000 }, (_, i) => i / 120).reduce(
    (state, time) =>
      advanceTrail(state, "a", time, 69 + Math.round(Math.sin(time) * 12)),
    emptyTrail,
  );
  expect(trail.samples.length).toBeLessThanOrEqual(400);
  expect(
    (trail.head?.time ?? 0) - (trail.samples[0]?.time ?? 0),
  ).toBeLessThanOrEqual(12);
  expect(advanceTrail(trail, "a", trail.head?.time ?? 0, null)).toEqual(trail);
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

it("uses a fixed C4–B5 range with equal semitone spacing independent of history", () => {
  expect(noteHeight(60)).toBe(-1);
  expect(noteHeight(83)).toBe(1);
  for (const midi of Array.from({ length: 23 }, (_, i) => 60 + i))
    expect(noteHeight(midi + 1) - noteHeight(midi)).toBeCloseTo(2 / 23);
  const first = advanceTrail(emptyTrail, "a", 0, 60);
  const octave = advanceTrail(first, "a", 0.1, 72);
  expect((octave.head?.pitch ?? 0) - (first.head?.pitch ?? 0)).toBeCloseTo(
    24 / 23,
  );
  const silent = advanceTrail(octave, "a", 0.2, null);
  expect(silent.head?.pitch).toBe(octave.head?.pitch);
  const next = advanceTrail(silent, "a", 0.3, 74);
  expect(next.head?.pitch).toBe(noteHeight(74));
  expect(
    advanceTrail(advanceTrail(next, "a", 0.4, 59), "a", 0.5, 74).head?.pitch,
  ).toBe(next.head?.pitch);
  expect(advanceTrail(emptyTrail, "a", 0, null).head?.pitch).toBe(0);
});

it("folds only notes outside the shared range into its nearest octave", () => {
  const notes = [0, 48, 59, 60, 71, 72, 83, 84, 96, 127];
  const expected = [60, 60, 71, 60, 71, 72, 83, 72, 72, 79];
  notes.reduce((state, midi, i) => {
    const next = advanceTrail(state, "roll", i / 10, midi);
    expect(next.head?.pitch).toBe(noteHeight(expected[i] ?? 0));
    return next;
  }, emptyTrail);
});
