/** Media-clock history: null pitch records a real unvoiced gap. */
export type MelodyPoint = Readonly<{ time: number; pitch: number | null }>;
export type MelodyTrail = Readonly<{
  source: string;
  head: MelodyPoint | null;
  samples: readonly MelodyPoint[];
}>;

export const trailSeconds = 12;
export const trailHeadX = 0.9;
export const emptyTrail: MelodyTrail = { source: "", head: null, samples: [] };

/** Bound storage independently of frame rate; duplicate paused observations do not grow it. */
export const advanceTrail = (
  previous: MelodyTrail,
  source: string,
  time: number,
  pitch: number | null,
): MelodyTrail => {
  const point = {
    time,
    pitch:
      pitch !== null && Number.isFinite(pitch)
        ? Math.max(-1, Math.min(1, pitch))
        : null,
  };
  const base = [
    previous.source !== source,
    time < (previous.head?.time ?? time),
  ].some(Boolean)
    ? emptyTrail
    : previous;
  const last = base.samples.at(-1);
  const append =
    last === undefined
      ? true
      : [
          time - last.time >= 1 / 30,
          (last.pitch === null) !== (point.pitch === null),
        ].some(Boolean);
  return Number.isFinite(time) && time >= 0
    ? {
        source,
        head: point,
        samples: (append ? [...base.samples, point] : base.samples)
          .filter((sample) => time - sample.time <= trailSeconds)
          .slice(-400),
      }
    : emptyTrail;
};

export type TrailPosition = Readonly<{ x: number; y: number }>;
/** Fixed right edge; every previous note moves left as the media clock advances. */
export const trailPosition = (
  point: MelodyPoint,
  now: number,
): TrailPosition => ({
  x: trailHeadX - (now - point.time) / trailSeconds,
  y: 0.52 - (point.pitch ?? 0) * 0.3,
});

/** Missing observations break the stroke rather than inventing a glide through silence. */
export const trailSegments = (
  trail: MelodyTrail,
): readonly (readonly [TrailPosition, TrailPosition])[] => {
  const head = trail.head;
  const points = head === null ? [] : [...trail.samples, head];
  return points.flatMap((point, index) => {
    const before = points[index - 1];
    return before !== undefined &&
      before.pitch !== null &&
      point.pitch !== null &&
      point.time > before.time &&
      point.time - before.time <= 0.25
      ? [
          [
            trailPosition(before, head?.time ?? 0),
            trailPosition(point, head?.time ?? 0),
          ] as const,
        ]
      : [];
  });
};
