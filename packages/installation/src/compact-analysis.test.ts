import { describe, expect, it } from "vitest";
import { Buffer } from "node:buffer";
import { compactTimeline } from "abservice-offline-player/analysis/compact";
import {
  analysisByteLimit,
  musicalMoment,
  parseMusicalAnalysis,
} from "./musical-analysis";

const metadata = {
  schemaVersion: 2,
  audioSha256: "a".repeat(64),
  analysisVersion: "b".repeat(64),
  engines: { melody: "c".repeat(64), harmony: "d".repeat(64) },
};
const at = (frame: number): number => (frame * 256) / 22050;
const midi = (bin: number): number => 69 + 12 * Math.log2(32.7 / 440) + bin / 5;
const sourceFrame = (frame: number) => ({
  bin: frame % 1292 < 10 ? -1 : frame % 1292 < 646 ? 180 : frame % 360,
  salience: Math.fround(0.35 + (frame % 91) / 140),
});
const checked = (input: unknown) =>
  parseMusicalAnalysis(input) ??
  (() => {
    throw Error("invalid fixture");
  })();

describe("compact musical timelines", () => {
  it("fits four voiced hours plus 200000 chords without dropping or quantizing any interpreted value", () => {
    const durationSeconds = 14400,
      count = Math.ceil((durationSeconds * 22050) / 256);
    const timeline = compactTimeline(durationSeconds);
    new Uint8Array(count).forEach((_, frame) => {
      const value = sourceFrame(frame);
      timeline.push(frame, value.bin, value.salience);
    });
    const chords = Array.from({ length: 200000 }, (_, i) => ({
      start: (i * durationSeconds) / 200000,
      end: ((i + 1) * durationSeconds) / 200000,
      root: i % 12,
      quality: "m7",
      bass: i % 12,
    }));
    const wire = { ...metadata, durationSeconds, ...timeline.finish(chords) };
    expect(Buffer.byteLength(JSON.stringify(wire))).toBeLessThan(
      analysisByteLimit,
    );
    const data = Buffer.from(wire.melody, "base64");
    expect(data.length).toBe(count * 6);
    expect(
      new Uint8Array(count).every((_, frame) => {
        const expected = sourceFrame(frame);
        return (
          data.readUInt16LE(frame * 6) ===
            (expected.bin < 0 ? 65535 : expected.bin) &&
          data.readFloatLE(frame * 6 + 2) ===
            (expected.bin < 0 ? 0 : expected.salience)
        );
      }),
    ).toBe(true);
    const harmony = Buffer.from(wire.harmony, "base64");
    expect(
      chords.every(
        (row, i) =>
          harmony.readDoubleLE(i * 19) === row.start &&
          harmony.readDoubleLE(i * 19 + 8) === row.end,
      ),
    ).toBe(true);
    const restored = checked(JSON.parse(JSON.stringify(wire)));
    expect(
      new Uint8Array(count).every((_, frame) =>
        frame % 32 === 0
          ? (() => {
              const value = sourceFrame(frame),
                moment = musicalMoment(restored, at(frame));
              return value.bin < 0
                ? moment.melody === null
                : moment.melody?.midi === midi(value.bin) &&
                    moment.melody.salience === value.salience &&
                    moment.melody.start === at(frame);
            })()
          : true,
      ),
    ).toBe(true);
    for (const frame of [0, 9, 10, 31, 32, 645, 646, 1291, 1292, count - 1]) {
      const expected = sourceFrame(frame),
        moment = musicalMoment(restored, at(frame));
      expect(moment.melody?.midi ?? null).toBe(
        expected.bin < 0 ? null : midi(expected.bin),
      );
      expect(musicalMoment(restored, at(frame) + 1e-9).melody).toEqual(
        moment.melody,
      );
    }
    expect(musicalMoment(restored, durationSeconds).melody).toBeNull();
    expect(musicalMoment(restored, -1).melody).toBeNull();
    expect(musicalMoment(restored, Number.NaN).harmony).toBeNull();
    expect(musicalMoment(restored, durationSeconds - 1e-6).harmony).toEqual(
      chords.at(-1),
    );
    expect(musicalMoment(restored, at(count - 1)).melody?.end).toBe(
      durationSeconds,
    );
  }, 60000);

  it("rejects malformed encodings, frame counts, invalid values, overlaps and unknown fields", () => {
    const timeline = compactTimeline(at(2));
    timeline.push(0, 120, 0.5);
    timeline.push(1, -1, 0);
    const wire = {
      ...metadata,
      durationSeconds: at(2),
      ...timeline.finish([
        { start: 0, end: at(2), root: 0, quality: "major", bass: null },
      ]),
    };
    const alter = (offset: number, value: number) => {
      const b = Buffer.from(wire.melody, "base64");
      b.writeFloatLE(value, offset);
      return b.toString("base64");
    };
    const invalidBin = Buffer.from(wire.melody, "base64");
    invalidBin.writeUInt16LE(360, 0);
    const invalidChord = Buffer.from(wire.harmony, "base64");
    invalidChord.writeDoubleLE(-1, 0);
    const reversed = Buffer.from(wire.harmony, "base64");
    reversed.writeDoubleLE(0, 8);
    const badQuality = Buffer.from(wire.harmony, "base64");
    badQuality.writeUInt8(13, 17);
    for (const value of [
      { ...wire, melody: wire.melody.slice(4) },
      { ...wire, melody: "!".repeat(wire.melody.length) },
      { ...wire, melody: alter(2, Infinity) },
      { ...wire, melody: alter(2, 1.1) },
      { ...wire, melody: alter(8, 0.5) },
      { ...wire, melody: invalidBin.toString("base64") },
      { ...wire, harmony: invalidChord.toString("base64") },
      { ...wire, harmony: reversed.toString("base64") },
      { ...wire, harmony: badQuality.toString("base64") },
      {
        ...wire,
        harmony: Buffer.concat([
          Buffer.from(wire.harmony, "base64"),
          Buffer.from(wire.harmony, "base64"),
        ]).toString("base64"),
      },
      { ...wire, extra: true },
      { ...wire, durationSeconds: 14401 },
    ])
      expect(parseMusicalAnalysis(value)).toBeUndefined();
    expect(() => compactTimeline(14401)).toThrow();
    expect(() => compactTimeline(1).finish([])).toThrow("Incomplete");
    expect(() => {
      compactTimeline(1).push(1, 120, 0.5);
    }).toThrow();
    expect(musicalMoment(checked(wire), at(1)).melody).toBeNull();
  });
});
