import type { TrailPosition } from "./trail";

const tau = Math.PI * 2;
type Segment = readonly [TrailPosition, TrailPosition];

/* Three pointed lobes with alternating underpasses in a single closed knot. */
const knotPoint = (t: number): TrailPosition => {
  const radius = (3 - 2 * Math.abs(Math.sin(1.5 * t))) / 3;
  return {
    x: radius * Math.cos(2 * t - Math.PI / 2),
    y: radius * Math.sin(2 * t - Math.PI / 2),
  };
};
const underpass = (t: number): boolean => {
  const distance =
    ((t - Math.PI / 6 + Math.PI / 3 + tau) % (tau / 3)) - Math.PI / 3;
  return Math.abs(distance) < 0.055;
};
const triquetra = Array.from({ length: 144 }, (_, i) => {
  const start = (i / 144) * tau;
  const end = ((i + 1) / 144) * tau;
  return underpass((start + end) / 2)
    ? []
    : [[knotPoint(start), knotPoint(end)] as const];
}).flat();

/* The tangent at the shared centre leads into three equally spaced, inward spirals. */
const spiralPoint = (arm: number, u: number): TrailPosition => {
  const angle = (arm * tau) / 3;
  const turn = u * tau * 1.45;
  const radius = 0.46 * (1 - u) ** 1.1;
  const x = 0.46 - radius * Math.cos(turn);
  const y = radius * Math.sin(turn);
  return {
    x: x * Math.cos(angle) - y * Math.sin(angle),
    y: x * Math.sin(angle) + y * Math.cos(angle),
  };
};
const triskele = Array.from({ length: 3 }, (_, arm) =>
  Array.from(
    { length: 56 },
    (_, i) =>
      [spiralPoint(arm, i / 56), spiralPoint(arm, (i + 1) / 56)] as const,
  ),
).flat();

/** Reuse the same clean vector outlines at every position and rotation. */
export const ornamentSegments = (kind: number): readonly Segment[] =>
  kind === 0 ? triquetra : triskele;
