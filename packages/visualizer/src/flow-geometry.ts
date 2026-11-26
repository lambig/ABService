/* eslint-disable functional/immutable-data -- The output buffer is private to this pure call; direct writes avoid per-vertex arrays in the rendering hot path. */
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
export const flowOrnaments = (time: number, offsets: readonly number[] = []) =>
  Array.from({ length: 5 }, (_, index) => {
    const id = Math.floor(time / 3.6) - index;
    const age = time - id * 3.6;
    const shifted = 0.06 + random(id) * 0.68 + (offsets[index] ?? 0);
    const folded = ((shifted % 1.6) + 1.6) % 1.6;
    return {
      id,
      x: 1.12 - age * 0.075,
      y: 0.1 + (folded <= 0.8 ? folded : 1.6 - folded),
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
  const segments = trailSegments(trail);
  const ornaments =
    trail.head === null
      ? []
      : flowOrnaments(trail.head.time, frame.flow?.offsets);
  const ornamentCount = ornaments.reduce(
    (count, ornament) => count + ornamentSegments(ornament.kind).length,
    0,
  );
  /* A stroke has six vertices of six floats; the quill adds 15 strokes and one triangle. */
  const output = new Float32Array(
    (segments.length + ornamentCount + 15) * 36 + 18,
  );
  const cursor = { offset: 0 };
  const vertex = (x: number, y: number, alpha: number): void => {
    output[cursor.offset] = (x / width) * 2 - 1;
    output[cursor.offset + 1] = 1 - (y / height) * 2;
    output[cursor.offset + 2] = ink[0] ?? 0;
    output[cursor.offset + 3] = ink[1] ?? 0;
    output[cursor.offset + 4] = ink[2] ?? 0;
    output[cursor.offset + 5] = alpha;
    cursor.offset += 6;
  };
  const stroke = (a: TrailPosition, b: TrailPosition, alpha: number): void => {
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const length = Math.hypot(dx, dy);
    const nx = (-dy / Math.max(length, 0.0001)) * 0.65;
    const ny = (dx / Math.max(length, 0.0001)) * 0.65;
    (length < 0.0001
      ? () => undefined
      : () => {
          vertex(a.x + nx, a.y + ny, alpha);
          vertex(a.x - nx, a.y - ny, alpha);
          vertex(b.x + nx, b.y + ny, alpha);
          vertex(b.x + nx, b.y + ny, alpha);
          vertex(a.x - nx, a.y - ny, alpha);
          vertex(b.x - nx, b.y - ny, alpha);
        })();
  };
  const pixel = (p: TrailPosition): TrailPosition => ({
    x: p.x * width,
    y: p.y * height,
  });
  segments.forEach(([a, b]) => {
    stroke(pixel(a), pixel(b), 0.95);
  });
  const head = trail.head;
  const center = head === null ? null : pixel(trailPosition(head, head.time));
  /* The nib touches the current melody position. The feather sits above/right,
   * leaving the existing trail visible; dimensions stay fixed in CSS pixels. */
  (center === null
    ? () => undefined
    : () => {
        const at = (x: number, y: number): TrailPosition => ({
          x: center.x + x,
          y: center.y + y,
        });
        const outline = [
          at(4, -8),
          at(3, -15),
          at(6, -23),
          at(13, -29),
          at(23, -34),
          at(22, -24),
          at(18, -16),
          at(12, -11),
          at(4, -8),
        ];
        const ribs = [
          [at(5, -16), at(9, -15), at(16, -15)],
          [at(8, -23), at(13, -21), at(20, -22)],
          [at(14, -28), at(17, -27), at(22, -28)],
        ];
        vertex(center.x, center.y, 1);
        vertex(center.x + 1, center.y - 6, 1);
        vertex(center.x + 5, center.y - 4, 1);
        stroke(at(2, -4), at(23, -34), 1);
        [outline, ...ribs].forEach((points) => {
          points.slice(1).forEach((point, i) => {
            stroke(points[i] ?? point, point, 1);
          });
        });
      })();
  ornaments.forEach((ornament) => {
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
    ornamentSegments(ornament.kind).forEach(([a, b]) => {
      stroke(transform(a.x, a.y), transform(b.x, b.y), 0.6);
    });
  });
  return output.subarray(0, cursor.offset);
};
