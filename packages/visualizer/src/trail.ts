/** Presentation height is retained through silence; raw analysis remains separate. */
export type MelodyPoint = Readonly<{ time: number; pitch: number }>;
export type MelodyTrail = Readonly<{
  source: string;
  head: MelodyPoint | null;
  samples: readonly MelodyPoint[];
}>;

export const trailSeconds = 12;
export const trailHeadX = 0.9;
export const emptyTrail: MelodyTrail = {
  source: "",
  head: null,
  samples: [],
};

/** Fixed within each planned section; only out-of-range notes move by octaves. */
export const noteHeight = (midi: number, low = 60): number => {
  const note = Math.round(midi);
  const folded =
    note < low
      ? note + Math.ceil((low - note) / 12) * 12
      : note > low + 23
        ? note - Math.ceil((note - low - 23) / 12) * 12
        : note;
  return ((folded - low) / 23) * 2 - 1;
};

/** Bound storage independently of frame rate; duplicate paused observations do not grow it. */
export const advanceTrail = (
  previous: MelodyTrail,
  source: string,
  time: number,
  midi: number | null,
  low = 60,
): MelodyTrail => {
  const base = [
    previous.source !== source,
    time < (previous.head?.time ?? time),
  ].some(Boolean)
    ? emptyTrail
    : previous;
  const height =
    midi !== null && Number.isFinite(midi)
      ? noteHeight(midi, low)
      : (base.head?.pitch ?? 0);
  const point = { time, pitch: height };
  const last = base.samples.at(-1);
  const append = last === undefined ? true : time - last.time >= 1 / 30;
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
  y: 0.44 - point.pitch * 0.3,
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
