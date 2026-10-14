import type { PresentationFrame } from "./index";
import { inkChannels } from "./ink";
import { emptyTrail, trailPosition, trailSegments } from "./trail";
import type { TrailPosition } from "./trail";

import { ornamentSegments } from "./ornaments";

const tau = Math.PI * 2;
const random = (seed: number): number => {
  const value = Math.sin(seed * 127.1 + 311.7) * 43758.5453;
  return value - Math.floor(value);
};

/** Reproducible random heights; births and rotation use media time, so pause also freezes ornaments. */
export const flowOrnaments = (time: number) =>
  Array.from({ length: 5 }, (_, index) => {
    const id = Math.floor(time / 3.6) - index;
    const age = time - id * 3.6;
    return {
      id,
      x: 1.12 - age * 0.075,
      y: 0.16 + random(id) * 0.68,
      angle: age * 0.32 + random(id + 80) * tau,
      radius: 22 + random(id + 160) * 12,
      kind: Math.abs(id) % 2,
    };
  });

/** Thin triangle strokes, not fullscreen per-pixel history searches. Coordinates are CSS pixels. */
export const flowVertices = (
  frame: PresentationFrame,
  width: number,
  height: number,
): Float32Array<ArrayBuffer> => {
  const trail = frame.trail ?? emptyTrail;
  const ink = inkChannels(frame).map((value) => value / 255);
  const vertex = (x: number, y: number, alpha: number): readonly number[] => [
    (x / width) * 2 - 1,
    1 - (y / height) * 2,
    ...ink,
    alpha,
  ];
  const stroke = (
    a: TrailPosition,
    b: TrailPosition,
    alpha: number,
  ): readonly number[] => {
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const length = Math.hypot(dx, dy);
    const nx = (-dy / Math.max(length, 0.0001)) * 0.65;
    const ny = (dx / Math.max(length, 0.0001)) * 0.65;
    return length < 0.0001
      ? []
      : [
          ...vertex(a.x + nx, a.y + ny, alpha),
          ...vertex(a.x - nx, a.y - ny, alpha),
          ...vertex(b.x + nx, b.y + ny, alpha),
          ...vertex(b.x + nx, b.y + ny, alpha),
          ...vertex(a.x - nx, a.y - ny, alpha),
          ...vertex(b.x - nx, b.y - ny, alpha),
        ];
  };
  const pixel = (p: TrailPosition): TrailPosition => ({
    x: p.x * width,
    y: p.y * height,
  });
  const line = trailSegments(trail).flatMap(([a, b]) =>
    stroke(pixel(a), pixel(b), 0.95),
  );
  const head = trail.head;
  const center = head === null ? null : pixel(trailPosition(head, head.time));
  const dot =
    center === null
      ? []
      : Array.from({ length: 16 }, (_, i) => [
          ...vertex(center.x, center.y, 1),
          ...vertex(
            center.x + Math.cos((i / 16) * tau) * 3,
            center.y + Math.sin((i / 16) * tau) * 3,
            1,
          ),
          ...vertex(
            center.x + Math.cos(((i + 1) / 16) * tau) * 3,
            center.y + Math.sin(((i + 1) / 16) * tau) * 3,
            1,
          ),
        ]).flat();
  const ornaments =
    head === null
      ? []
      : flowOrnaments(head.time).flatMap((ornament) => {
          const transform = (x: number, y: number): TrailPosition => ({
            x:
              ornament.x * width +
              (x * Math.cos(ornament.angle) - y * Math.sin(ornament.angle)) *
                ornament.radius,
            y:
              ornament.y * height +
              (x * Math.sin(ornament.angle) + y * Math.cos(ornament.angle)) *
                ornament.radius,
          });
          return ornamentSegments(ornament.kind).flatMap(([a, b]) =>
            stroke(transform(a.x, a.y), transform(b.x, b.y), 0.6),
          );
        });
  return new Float32Array([...line, ...dot, ...ornaments]);
};
