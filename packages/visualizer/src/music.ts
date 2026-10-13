/** Media-clock interpretation already accepted by the musical analysis layer. */
export type MusicalExpressionInput = Readonly<{
  position: number;
  melody: Readonly<{ midi: number }> | null;
  harmony: Readonly<{ root: number; quality: string }> | null;
}>;

/** Independent melodic height and harmonic field; absent notes stay absent. */
export type MusicalExpression = Readonly<{
  pitch: number;
  voiced: number;
  harmonyPhase: number;
  harmonyActive: number;
}>;

/** Neutral musical contribution, used on missing data, stop and item changes. */
export const restingMusic: MusicalExpression = Object.freeze({
  pitch: 0,
  voiced: 0,
  harmonyPhase: 0,
  harmonyActive: 0,
});

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
  const shade = ["minor", "m7"].includes(quality ?? "")
    ? -0.035
    : ["sus4", "sus2", "5"].includes(quality ?? "")
      ? 0.035
      : 0;
  return Object.freeze({
    pitch: voiced ? Math.max(-1, Math.min(1, (midi - 69) / 24)) : 0,
    voiced: voiced ? 1 : 0,
    harmonyPhase: harmony ? ((root * 7) % 12) / 12 + shade : 0,
    harmonyActive: harmony ? 1 : 0,
  });
};
