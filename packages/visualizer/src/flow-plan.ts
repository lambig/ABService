/* eslint-disable functional/immutable-data -- Bounded preprocessing uses local typed arrays; the returned plan is only read during playback. */
export const sceneStep = 0.1;
export type FlowPlan = Readonly<{
  ranges: readonly Readonly<{ time: number; low: number }>[];
  drift: Float64Array;
}>;
export type FlowMoment = Readonly<{
  low: number;
  offsets: readonly number[];
}>;

/** Artistic directions, not a musical classification or a key estimate. */
export const chordVelocity = (quality: string | undefined): number =>
  ({
    major: -0.012,
    maj7: -0.009,
    "6": -0.01,
    "7": -0.006,
    minor: 0.012,
    m7: 0.009,
    m6: 0.01,
    dim: 0.016,
    m7b5: 0.014,
    aug: -0.016,
    sus2: 0,
    sus4: 0,
    "5": 0,
  })[quality ?? ""] ?? 0;

/** One plan for the entire analysis: minimize folded duration plus a cost for changing register. */
export const planFlow = (
  pitches: Int16Array,
  velocities: Float32Array,
): FlowPlan => {
  const count = pitches.length;
  const cuts = [0];
  for (const target of Array.from(
    { length: Math.ceil(count / 80) },
    (_, i) => (i + 1) * 80,
  ).filter((i) => i < count - 20)) {
    const rests = Array.from({ length: 41 }, (_, i) => target - 20 + i).filter(
      (i) =>
        i >= (cuts.at(-1) ?? 0) + 60 &&
        i < count - 1 &&
        pitches[i - 1] === -1 &&
        pitches[i] === -1 &&
        pitches[i + 1] === -1,
    );
    const cut = rests.reduce(
      (best, i) => (Math.abs(i - target) < Math.abs(best - target) ? i : best),
      rests[0] ?? target,
    );
    cuts.push(cut);
  }
  cuts.push(count);
  const lows = Array.from({ length: 10 }, (_, octave) => octave * 12);
  const paths: number[][] = [];
  const cell = {
    previous: lows.map((low) => (Math.abs(low - 60) / 12) * 0.05),
    choice: 0,
    speed: 0,
  };
  for (const block of cuts.slice(0, -1).map((_, i) => i)) {
    const first = cuts[block] ?? 0,
      end = cuts[block + 1] ?? count;
    const loss = lows.map(() => 0);
    pitches.subarray(first, end).forEach((midi) => {
      lows.forEach((low, j) => {
        loss[j] =
          (loss[j] ?? 0) +
          (midi >= 0 && [midi < low, midi > low + 23].some(Boolean)
            ? sceneStep
            : 0);
      });
    });
    const rest =
      first > 0 && pitches[first - 1] === -1 && pitches[first] === -1;
    const parents: number[] = [];
    cell.previous = lows.map((low, j) => {
      const costs = cell.previous.map(
        (cost, k) =>
          cost +
          ([j === k, block === 0].some(Boolean)
            ? 0
            : (rest ? 0.8 : 3) + (Math.abs(low - (lows[k] ?? 60)) / 12) * 0.4),
      );
      const best = block === 0 ? j : costs.indexOf(Math.min(...costs));
      parents.push(best);
      return (costs[best] ?? 0) + (loss[j] ?? 0);
    });
    paths.push(parents);
  }
  cell.choice = cell.previous.indexOf(Math.min(...cell.previous));
  const ranges: { time: number; low: number }[] = [];
  for (const block of paths.map((_, i) => i).reverse()) {
    ranges.push({
      time: (cuts[block] ?? 0) * sceneStep,
      low: lows[cell.choice] ?? 60,
    });
    cell.choice = paths[block]?.[cell.choice] ?? cell.choice;
  }
  ranges.reverse();
  const drift = new Float64Array(count + 1);
  const alpha = 1 - Math.exp(-sceneStep / 0.8);
  pitches.forEach((_, i) => {
    const next = cell.speed + ((velocities[i] ?? 0) - cell.speed) * alpha;
    drift[i + 1] = (drift[i] ?? 0) + (cell.speed + next) * 0.5 * sceneStep;
    cell.speed = next;
  });
  return Object.freeze({
    ranges: ranges.filter((r, i) =>
      [i === 0, r.low !== ranges[i - 1]?.low].some(Boolean),
    ),
    drift,
  });
};

/** Media-time lookup makes pauses and arbitrary seeks deterministic. */
export const flowMoment = (plan: FlowPlan, time: number): FlowMoment => {
  const at = (t: number): number => {
    const sample = Math.max(0, Math.min(plan.drift.length - 1, t / sceneStep));
    const i = Math.floor(sample),
      a = plan.drift[i] ?? 0;
    return a + ((plan.drift[i + 1] ?? a) - a) * (sample - i);
  };
  const find = (left: number, right: number): number => {
    const middle = Math.floor((left + right) / 2);
    return left >= right
      ? left - 1
      : (plan.ranges[middle]?.time ?? Infinity) <= time
        ? find(middle + 1, right)
        : find(left, middle);
  };
  return {
    low: plan.ranges[Math.max(0, find(0, plan.ranges.length))]?.low ?? 60,
    offsets: Array.from(
      { length: 5 },
      (_, i) => at(time) - at(Math.max(0, (Math.floor(time / 3.6) - i) * 3.6)),
    ),
  };
};
