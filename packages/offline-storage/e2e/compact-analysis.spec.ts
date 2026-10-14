import { test, expect } from "@playwright/test";

test("four-hour synthetic result fits OPFS and retains its final frame after reload", async ({
  page,
}) => {
  await page.goto("/");
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
  const saved = await page.evaluate(async () => {
    const h = window.storageHarness,
      signal = new AbortController().signal;
    const source = await (await h.open()).save("../音源/one", h.first, signal);
    await (source.kind === "ok"
      ? Promise.resolve()
      : Promise.reject(Error("Source fixture save failed")));
    /* This stresses result storage, not four-hour DSP. The legacy manifest has no declared duration. */
    const durationSeconds = 14400,
      count = Math.ceil((durationSeconds * 22050) / 256);
    const timeline = h.compactTimeline(durationSeconds);
    new Uint8Array(count).forEach((_, i) => {
      timeline.push(i, i % 360, Math.fround(0.4 + (i % 50) / 100));
    });
    const wire = {
      schemaVersion: 2,
      durationSeconds,
      audioSha256: await h.digest(h.first),
      analysisVersion: "b".repeat(64),
      engines: { melody: "c".repeat(64), harmony: "d".repeat(64) },
      ...timeline.finish(
        Array.from({ length: 200000 }, (_, i) => ({
          start: (i * durationSeconds) / 200000,
          end: ((i + 1) * durationSeconds) / 200000,
          root: i % 12,
          quality: "m7",
          bass: null,
        })),
      ),
    };
    const blob = new Blob([JSON.stringify(wire)]);
    const result = await h
      .createAnalysisStore(await h.manifest())
      .save(blob, signal);
    return { bytes: blob.size, kind: result.kind };
  });
  expect(saved).toEqual({ bytes: 14989571, kind: "ok" });
  await page.reload();
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
  const restored = await page.evaluate(async () => {
    const h = window.storageHarness;
    const result = await h
      .createAnalysisStore(await h.manifest())
      .read(await h.digest(h.first), new AbortController().signal);
    const value = result.kind === "ok" ? result.value : undefined;
    const analysis = await (value === undefined
      ? Promise.reject(Error("Missing stored analysis"))
      : Promise.resolve(value));
    const last = Math.ceil((14400 * 22050) / 256) - 1;
    return {
      schema: analysis.schemaVersion,
      moment: h.musicalMoment(analysis, (last * 256) / 22050),
      end: h.musicalMoment(analysis, 14400),
    };
  });
  const last = Math.ceil((14400 * 22050) / 256) - 1;
  expect(restored).toEqual({
    schema: 2,
    moment: {
      position: (last * 256) / 22050,
      melody: {
        start: (last * 256) / 22050,
        end: 14400,
        midi: 69 + 12 * Math.log2(32.7 / 440) + (last % 360) / 5,
        salience: Math.fround(0.4 + (last % 50) / 100),
      },
      harmony: {
        start: (199999 * 14400) / 200000,
        end: 14400,
        root: 199999 % 12,
        quality: "m7",
        bass: null,
      },
    },
    end: { position: 14400, melody: null, harmony: null },
  });
});
