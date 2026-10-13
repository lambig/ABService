import { z } from "zod";

/** Bound parsing cost on the listening device; durations and gaps remain explicit. */
export const analysisByteLimit = 16 * 1024 * 1024;
const sha256 = z.string().regex(/^[a-f0-9]{64}$/);
const seconds = z.number().nonnegative();
const span = { start: seconds, end: seconds };
const melody = z
  .object({
    ...span,
    midi: z.number().min(0).max(127),
    salience: z.number().min(0).max(1),
  })
  .strict()
  .readonly();
const harmony = z
  .object({
    ...span,
    root: z.number().int().min(0).max(11),
    quality: z.enum([
      "major",
      "minor",
      "7",
      "maj7",
      "m7",
      "dim",
      "sus4",
      "5",
      "aug",
      "sus2",
      "m7b5",
      "6",
      "m6",
    ]),
    bass: z.number().int().min(0).max(11).nullable(),
  })
  .strict()
  .readonly();
const ordered = (
  rows: readonly Readonly<{ start: number; end: number }>[],
  duration: number,
): boolean =>
  rows.every(
    (row, index) =>
      row.start < row.end &&
      row.end <= duration &&
      (index === 0 ? true : row.start >= (rows[index - 1]?.end ?? 0)),
  );

const schema = z
  .object({
    schemaVersion: z.literal(1),
    audioSha256: sha256,
    durationSeconds: seconds.positive().max(14400),
    analysisVersion: sha256,
    /* Exact PC engine/model/dictionary/parameters identities, without local paths. */
    engines: z.object({ melody: sha256, harmony: sha256 }).strict().readonly(),
    melody: z.array(melody).max(200000).readonly(),
    harmony: z.array(harmony).max(200000).readonly(),
  })
  .strict()
  .refine(
    (value) =>
      value.melody.length + value.harmony.length <= 200000 &&
      ordered(value.melody, value.durationSeconds) &&
      ordered(value.harmony, value.durationSeconds),
    "invalid timeline ordering or extent",
  )
  .readonly();

/** PC-produced analysis of the exact encoded audio bytes; missing intervals mean unknown. */
export type MusicalAnalysis = z.infer<typeof schema>;
/** Point-in-time interpretation. Salience is a model output, not a calibrated probability. */
export type MusicalMoment = Readonly<{
  position: number;
  melody: MusicalAnalysis["melody"][number] | null;
  harmony: MusicalAnalysis["harmony"][number] | null;
}>;

/** Validates a complete immutable result without extending notes through unknown intervals. */
export const parseMusicalAnalysis = (
  input: unknown,
): MusicalAnalysis | undefined => {
  const result = schema.safeParse(input);
  return result.success ? result.data : undefined;
};

/* Binary search starts afresh at the current media clock, so seek and loop need no cursor repair. */
const at = <T extends Readonly<{ start: number; end: number }>>(
  rows: readonly T[],
  time: number,
): T | null => {
  const search = (low: number, high: number): T | null => {
    const middle = Math.floor((low + high) / 2);
    const row = rows[middle];
    return low >= high
      ? null
      : row === undefined
        ? null
        : time < row.start
          ? search(low, middle)
          : time >= row.end
            ? search(middle + 1, high)
            : row;
  };
  return search(0, rows.length);
};

/** Reads half-open intervals against media.currentTime, without extrapolation or rhythmic inference. */
export const musicalMoment = (
  analysis: MusicalAnalysis,
  position: number,
): MusicalMoment =>
  Object.freeze({
    position,
    melody: Number.isFinite(position) ? at(analysis.melody, position) : null,
    harmony: Number.isFinite(position) ? at(analysis.harmony, position) : null,
  });
