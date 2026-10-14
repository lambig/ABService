import { expect, it } from "vitest";
import {
  advanceTrail,
  emptyTrail,
  trailHeadX,
  trailPosition,
  trailSegments,
} from "./trail";
import { flowOrnaments, flowVertices } from "./flow-geometry";
import { restingFrame } from "./index";

it("keeps the melody dot at the right and sends the previous pitches left", () => {
  const first = advanceTrail(emptyTrail, "a", 0, -0.5);
  const second = advanceTrail(first, "a", 0.1, 0.5);
  const third = advanceTrail(second, "a", 0.2, 0);
  const old = trailSegments(second)[0];
  const moved = trailSegments(third)[0];
  expect(old?.[1].x).toBe(trailHeadX);
  expect(old?.[1].y).toBeLessThan(old?.[0].y ?? 0);
  expect(moved?.[0].y).toBe(old?.[0].y);
  expect(moved?.[0].x).toBeLessThan(old?.[0].x ?? 0);
  expect(trailPosition({ time: 0.2, pitch: 0 }, 0.2).x).toBe(trailHeadX);
});

it("does not join across unvoiced intervals, missing observations, seeks or source changes", () => {
  const samples = [0, 0.1, null, null, 0.5, 0.6];
  const trail = samples.reduce(
    (state, pitch, i) => advanceTrail(state, "a", i / 10, pitch),
    emptyTrail,
  );
  expect(trailSegments(trail)).toHaveLength(2);
  const stalled = advanceTrail(trail, "a", 1, 0.7);
  expect(trailSegments(stalled)).toHaveLength(2);
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
  expect(vertices.byteLength).toBeLessThanOrEqual(160000);
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
