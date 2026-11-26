import { expect, it } from "vitest";
import { chordVelocity, flowMoment, planFlow } from "./flow-plan";
import { advanceTrail, emptyTrail } from "./trail";
import { flowOrnaments } from "./flow-geometry";

it("reduces folding across sustained registers, using the nearby rest for the change", () => {
  const notes = Int16Array.from({ length: 320 }, (_, i) =>
    i < 76 ? 50 + (i % 12) : i < 84 ? -1 : 86 + (i % 12),
  );
  const plan = planFlow(notes, new Float32Array(notes.length));
  expect(plan.ranges).toHaveLength(2);
  expect(plan.ranges[0]?.low).toBe(48);
  expect(plan.ranges[1]?.low).toBe(84);
  expect(plan.ranges[1]?.time).toBeGreaterThanOrEqual(7.6);
  expect(plan.ranges[1]?.time).toBeLessThanOrEqual(8.3);
  expect(flowMoment(plan, 20).low).toBe(84);
  expect(flowMoment(plan, 1).low).toBe(48);
});

it("does not move the range for an isolated octave error; silence keeps the common default", () => {
  const pitches = Int16Array.from({ length: 320 }, (_, i) =>
    i === 120 ? 100 : 60 + (i % 24),
  );
  expect(planFlow(pitches, new Float32Array(320)).ranges).toEqual([
    { time: 0, low: 60 },
  ]);
  expect(
    planFlow(new Int16Array(320).fill(-1), new Float32Array(320)).ranges,
  ).toEqual([{ time: 0, low: 60 }]);
  const before = advanceTrail(emptyTrail, "a", 0, 60, 48);
  const after = advanceTrail(before, "a", 0.1, 84, 84);
  expect(after.samples[0]).toEqual(before.samples[0]);
});

it("integrates smooth chord-type motion on the media clock with reproducible seeks and births", () => {
  const velocities = Float32Array.from({ length: 400 }, (_, i) =>
    chordVelocity(i < 200 ? "major" : "minor"),
  );
  const plan = planFlow(new Int16Array(400).fill(60), velocities);
  expect(chordVelocity("sus4")).toBe(0);
  expect(plan.drift[100]).toBeLessThan(0);
  expect((plan.drift[390] ?? 0) - (plan.drift[300] ?? 0)).toBeGreaterThan(0);
  expect(
    Math.abs((plan.drift[201] ?? 0) - (plan.drift[200] ?? 0)),
  ).toBeLessThan(0.0013);
  const scene = (time: number) =>
    flowOrnaments(time, flowMoment(plan, time).offsets);
  const first = scene(8);
  scene(30);
  expect(scene(8)).toEqual(first);
  const before = scene(7.19),
    after = scene(7.21);
  before
    .filter((a) => after.some((b) => b.id === a.id))
    .forEach((a) => {
      expect(
        Math.abs(a.y - (after.find((b) => b.id === a.id)?.y ?? 0)),
      ).toBeLessThan(0.001);
    });
  expect(scene(30).every((p) => p.y >= 0.1 && p.y <= 0.9)).toBe(true);
});
