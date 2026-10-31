/* eslint-disable functional/immutable-data -- A temporary Blob hook rejects audio body reads and is restored after each scenario. */
import { test, expect } from "@playwright/test";

test("analysis follows unchanged audio across package versions without reading audio bodies", async ({
  page,
}) => {
  await page.goto("/");
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
  const result = await page.evaluate(async () => {
    const h = window.storageHarness;
    const signal = new AbortController().signal;
    const original = await h.manifest();
    const missingFixture = (): never => {
      throw Error("Missing fixture");
    };
    const asset = original.assets[0] ?? missingFixture();
    const wire = {
      schemaVersion: 1,
      durationSeconds: 1,
      audioSha256: asset.checksum.value,
      analysisVersion: "a".repeat(64),
      engines: { melody: "b".repeat(64), harmony: "c".repeat(64) },
      melody: [{ start: 0, end: 1, midi: 60, salience: 0.7 }],
      harmony: [{ start: 0, end: 1, root: 0, quality: "5", bass: null }],
    };
    const blob = new Blob([JSON.stringify(wire)]);
    const old = h.createAnalysisStore(original);
    const missing = await old.save(blob, signal);
    await (await h.open()).save(asset.assetId, h.first, signal);
    const name = await h.digest(
      new Blob([
        JSON.stringify([
          asset.assetId,
          asset.byteLength,
          asset.checksum.algorithm,
          asset.checksum.value,
        ]),
      ]),
    );
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Called on the original Blob receiver and restored below. */
    const slice = Blob.prototype.slice;
    const unexpectedRead = (): never => {
      throw Error("Unexpected audio read");
    };
    Blob.prototype.slice = function (start, end, type) {
      return this instanceof File && this.name === name
        ? unexpectedRead()
        : slice.call(this, start, end, type);
    };
    try {
      const saved = await old.save(blob, signal);
      const updated = h.createAnalysisStore({
        ...original,
        packageVersion: "artwork-updated",
      });
      const reused = await updated.read(asset.checksum.value, signal);
      const foreign = await h
        .createAnalysisStore(await h.manifest(h.second))
        .read(asset.checksum.value, signal);
      const files = await (
        await navigator.storage.getDirectory()
      ).getDirectoryHandle("abservice-assets-v1");
      const writer = await (await files.getFileHandle(name)).createWritable();
      await writer.write(new Uint8Array(asset.byteLength - 1));
      await writer.close();
      const truncated = await updated.save(blob, signal);
      return { missing, saved, reused, foreign, truncated };
    } finally {
      Blob.prototype.slice = slice;
    }
  });
  expect(result).toMatchObject({
    missing: { kind: "error", error: "missing" },
    saved: { kind: "ok" },
    reused: { kind: "ok", value: { analysisVersion: "a".repeat(64) } },
    foreign: { kind: "error", error: "unknown-asset" },
    truncated: { kind: "error", error: "corrupt" },
  });
});

test("existing package-scoped analysis remains readable before its next import", async ({
  page,
}) => {
  await page.goto("/");
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
  const result = await page.evaluate(async () => {
    const h = window.storageHarness,
      signal = new AbortController().signal;
    const manifest = await h.manifest();
    const audioSha256 = await h.digest(h.first);
    const body = JSON.stringify({
      schemaVersion: 1,
      durationSeconds: 1,
      audioSha256,
      analysisVersion: "a".repeat(64),
      engines: { melody: "b".repeat(64), harmony: "c".repeat(64) },
      melody: [],
      harmony: [],
    });
    const root = await (
      await navigator.storage.getDirectory()
    ).getDirectoryHandle("abservice-musical-analysis-v1", { create: true });
    const legacy = await root.getFileHandle(
      await h.digest(
        new Blob([JSON.stringify([manifest.packageVersion, audioSha256])]),
      ),
      { create: true },
    );
    const writer = await legacy.createWritable();
    await writer.write(`${await h.digest(new Blob([body]))}\n${body}`);
    await writer.close();
    return h.createAnalysisStore(manifest).read(audioSha256, signal);
  });
  expect(result).toMatchObject({
    kind: "ok",
    value: { analysisVersion: "a".repeat(64) },
  });
});

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
