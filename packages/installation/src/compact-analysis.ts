import { z } from "zod";

export const chordQualities = [
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
] as const;
export type MelodyInterval = Readonly<{
  start: number;
  end: number;
  midi: number;
  salience: number;
}>;
export type HarmonyInterval = Readonly<{
  start: number;
  end: number;
  root: number;
  quality: (typeof chordQualities)[number];
  bass: number | null;
}>;
const hash = z.string().regex(/^[a-f0-9]{64}$/);
const schema = z
  .object({
    schemaVersion: z.literal(2),
    audioSha256: hash,
    durationSeconds: z.number().positive().max(14400),
    analysisVersion: hash,
    engines: z.object({ melody: hash, harmony: hash }).strict().readonly(),
    melody: z.string().max(9922504),
    harmony: z.string().max(5066668),
  })
  .strict()
  .readonly();
export type CompactAnalysis = z.infer<typeof schema>;
type Decoded = Readonly<{ melody: DataView; harmony: DataView }>;
const cache = new WeakMap<CompactAnalysis, Decoded>();
const frames = (duration: number): number =>
  Math.ceil((duration * 22050) / 256);
const time = (frame: number): number => (frame * 256) / 22050;
const decode = (text: string): DataView => {
  const raw = atob(text);
  return new DataView(Uint8Array.from(raw, (c) => c.charCodeAt(0)).buffer);
};
const base64 = (text: string): boolean =>
  text.length % 4 === 0 && /^[A-Za-z0-9+/=]*$/.test(text);
const everyRecord = (
  view: DataView,
  width: number,
  predicate: (index: number) => boolean,
): boolean =>
  new Uint8Array(view.buffer).every((_, byte) =>
    byte % width === 0 ? predicate(byte / width) : true,
  );
const chord = (view: DataView, index: number): HarmonyInterval => {
  const offset = index * 19;
  return Object.freeze({
    start: view.getFloat64(offset, true),
    end: view.getFloat64(offset + 8, true),
    root: view.getUint8(offset + 16),
    quality: chordQualities[
      view.getUint8(offset + 17)
    ] as (typeof chordQualities)[number],
    bass:
      view.getUint8(offset + 18) === 255 ? null : view.getUint8(offset + 18),
  });
};
const valid = (value: CompactAnalysis): Decoded | undefined => {
  try {
    const count = frames(value.durationSeconds);
    const expected = 4 * Math.ceil((count * 6) / 3);
    const format =
      value.melody.length === expected &&
      base64(value.melody) &&
      base64(value.harmony);
    const read = (): Decoded | undefined => {
      const melody = decode(value.melody),
        harmony = decode(value.harmony);
      const melodyValid =
        melody.byteLength === count * 6 &&
        everyRecord(melody, 6, (i) => {
          const bin = melody.getUint16(i * 6, true),
            salience = melody.getFloat32(i * 6 + 2, true);
          return bin === 65535
            ? salience === 0
            : bin < 360 &&
                Number.isFinite(salience) &&
                salience >= 0 &&
                salience <= 1;
        });
      const harmonyValid =
        harmony.byteLength % 19 === 0 &&
        harmony.byteLength <= 200000 * 19 &&
        everyRecord(harmony, 19, (i) => {
          const row = chord(harmony, i);
          return (
            Number.isFinite(row.start) &&
            row.start >= 0 &&
            row.start < row.end &&
            row.end <= value.durationSeconds &&
            row.root <= 11 &&
            harmony.getUint8(i * 19 + 17) < chordQualities.length &&
            (row.bass === null ? true : row.bass <= 11) &&
            (i === 0
              ? true
              : row.start >= harmony.getFloat64((i - 1) * 19 + 8, true))
          );
        });
      return melodyValid && harmonyValid ? { melody, harmony } : undefined;
    };
    return format ? read() : undefined;
  } catch {
    return undefined;
  }
};

/** Validates fixed-grid float32 melody and exact float64 chord spans without expanding per-frame objects. */
export const parseCompactAnalysis = (
  input: unknown,
): CompactAnalysis | undefined => {
  const parsed = schema.safeParse(input);
  const value = parsed.success ? parsed.data : undefined;
  const bytes = value === undefined ? undefined : valid(value);
  return value !== undefined && bytes !== undefined
    ? (() => {
        cache.set(value, bytes);
        return value;
      })()
    : undefined;
};

/** Returns the original half-open frame/chord intervals at media time, including gaps and the clipped final frame. */
export const compactMoment = (
  value: CompactAnalysis,
  position: number,
): Readonly<{
  melody: MelodyInterval | null;
  harmony: HarmonyInterval | null;
}> => {
  const bytes = cache.get(value);
  const read = (data: Decoded) => {
    const approximate = Math.floor((position * 22050) / 256);
    /* Inverting the frame-to-time division may round either side of an exact boundary. */
    const frame =
      position < time(approximate)
        ? approximate - 1
        : position >= time(approximate + 1)
          ? approximate + 1
          : approximate;
    const bin = data.melody.getUint16(frame * 6, true);
    const find = (low: number, high: number): HarmonyInterval | null => {
      const middle = Math.floor((low + high) / 2);
      return low >= high
        ? null
        : (() => {
            const row = chord(data.harmony, middle);
            return position < row.start
              ? find(low, middle)
              : position >= row.end
                ? find(middle + 1, high)
                : row;
          })();
    };
    return {
      melody:
        bin === 65535
          ? null
          : Object.freeze({
              start: time(frame),
              end: Math.min(value.durationSeconds, time(frame + 1)),
              midi: 69 + 12 * Math.log2(32.7 / 440) + bin / 5,
              salience: data.melody.getFloat32(frame * 6 + 2, true),
            }),
      harmony: find(0, data.harmony.byteLength / 19),
    };
  };
  return bytes !== undefined &&
    Number.isFinite(position) &&
    position >= 0 &&
    position < value.durationSeconds
    ? read(bytes)
    : { melody: null, harmony: null };
};
