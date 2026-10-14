/** Presentation height is retained through silence; raw analysis remains separate. */
export type MelodyPoint = Readonly<{ time: number; pitch: number }>;
export type MelodyTrail = Readonly<{
  source: string;
  head: MelodyPoint | null;
  samples: readonly MelodyPoint[];
  rawPitch: number | null;
}>;

export const trailSeconds = 12;
export const trailHeadX = 0.9;
export const emptyTrail: MelodyTrail = {
  source: "",
  head: null,
  samples: [],
  rawPitch: null,
};

/** Fold only octave-like large jumps; ordinary intervals keep their direction and size. */
export const melodicStep = (semitones: number): number => {
  const nearby = ((((semitones + 6) % 12) + 12) % 12) - 6;
  return Math.abs(semitones) >= 10 && Math.abs(nearby) <= 2
    ? nearby
    : semitones;
};

/** Bound storage independently of frame rate; duplicate paused observations do not grow it. */
export const advanceTrail = (
  previous: MelodyTrail,
  source: string,
  time: number,
  pitch: number | null,
): MelodyTrail => {
  const base = [
    previous.source !== source,
    time < (previous.head?.time ?? time),
  ].some(Boolean)
    ? emptyTrail
    : previous;
  const raw = pitch !== null && Number.isFinite(pitch) ? pitch : null;
  const height =
    raw === null
      ? (base.head?.pitch ?? 0)
      : base.rawPitch === null
        ? raw
        : (base.head?.pitch ?? raw) +
          melodicStep((raw - base.rawPitch) * 24) / 24;
  const point = { time, pitch: Math.max(-1, Math.min(1, height)) };
  const last = base.samples.at(-1);
  const append = last === undefined ? true : time - last.time >= 1 / 30;
  return Number.isFinite(time) && time >= 0
    ? {
        source,
        rawPitch: raw ?? base.rawPitch,
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
  y: 0.52 - point.pitch * 0.3,
});

/** Silence carries the last height; seeks clear the history at the playback boundary. */
export const trailSegments = (
  trail: MelodyTrail,
): readonly (readonly [TrailPosition, TrailPosition])[] => {
  const head = trail.head;
  const points = head === null ? [] : [...trail.samples, head];
  return points.flatMap((point, index) => {
    const before = points[index - 1];
    return before !== undefined &&
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
