/** Media-clock interpretation already accepted by the musical analysis layer. */
export type MusicalExpressionInput = Readonly<{
  position: number;
  melody: Readonly<{ midi: number; salience?: number }> | null;
  harmony: Readonly<{ root: number; quality: string }> | null;
}>;

/** Independent melodic height and harmonic field; absent notes stay absent. */
export type MusicalExpression = Readonly<{
  pitch: number;
  voiced: number;
  harmonyPhase: number;
  harmonyRoot: number;
  harmonyActive: number;
  third: number;
  fifth: number;
  extension: number;
  position: number;
}>;

/** Neutral musical contribution, used on missing data, stop and item changes. */
export const restingMusic: MusicalExpression = Object.freeze({
  pitch: 0,
  voiced: 0,
  harmonyPhase: 0,
  harmonyRoot: 0,
  harmonyActive: 0,
  third: 0,
  fifth: 0,
  extension: 0,
  position: 0,
});

/* Semitone spacing controls the field geometry independently of its root hue. */
const voicing: Readonly<Record<string, readonly [number, number, number]>> = {
  major: [4, 7, 0],
  minor: [3, 7, 0],
  "7": [4, 7, 10],
  maj7: [4, 7, 11],
  m7: [3, 7, 10],
  dim: [3, 6, 0],
  sus4: [5, 7, 0],
  "5": [7, 12, 0],
  aug: [4, 8, 0],
  sus2: [2, 7, 0],
  m7b5: [3, 6, 10],
  "6": [4, 7, 9],
  m6: [3, 7, 9],
};

/** Maps pitch to vertical space and chord roots around a circle of fifths. */
export const mapMusicalExpression = (
  input: MusicalExpressionInput | null,
): MusicalExpression => {
  const midi = input?.melody?.midi;
  const root = input?.harmony?.root;
  const voiced = midi !== undefined && Number.isFinite(midi);
  const harmony =
    root !== undefined && Number.isInteger(root) && root >= 0 && root < 12;
  const quality = input?.harmony?.quality;
  const intervals = voicing[quality ?? ""];
  const shade = ["minor", "m7"].includes(quality ?? "")
    ? -0.035
    : ["sus4", "sus2", "5"].includes(quality ?? "")
      ? 0.035
      : 0;
  return Object.freeze({
    pitch: voiced ? Math.max(-1, Math.min(1, (midi - 69) / 24)) : 0,
    voiced: voiced ? 1 : 0,
    harmonyPhase: harmony ? ((root * 7) % 12) / 12 + shade : 0,
    harmonyRoot: harmony ? root : 0,
    harmonyActive: harmony ? 1 : 0,
    third: harmony ? (intervals?.[0] ?? 4) / 12 : 0,
    fifth: harmony ? (intervals?.[1] ?? 7) / 12 : 0,
    extension: harmony ? (intervals?.[2] ?? 0) / 12 : 0,
    position:
      [voiced, harmony].some(Boolean) && Number.isFinite(input?.position)
        ? (input?.position ?? 0)
        : 0,
  });
};
