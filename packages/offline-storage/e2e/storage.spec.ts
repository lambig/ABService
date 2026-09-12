/* eslint-disable functional/immutable-data -- Browser failure injection temporarily replaces native methods and restores them in finally blocks. */
import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
});

test("real OPFS survives reload; verified audio can be read without network", async ({
  page,
  context,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      return (await h.open()).save(
        "../音源/one",
        h.first,
        new AbortController().signal,
      );
    }),
  ).toEqual({ kind: "ok" });
  await page.reload();
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
  await context.setOffline(true);
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = await h.open();
      const result = await store.read(
        "../音源/one",
        new AbortController().signal,
      );
      return result.kind === "ok"
        ? {
            type: result.value.type,
            sameBytes:
              (await h.digest(result.value)) === (await h.digest(h.first)),
            readiness: await store.assess(
              [1, 0, 0],
              new AbortController().signal,
            ),
          }
        : result;
    }),
  ).toMatchObject({
    type: "audio/flac",
    sameBytes: true,
    readiness: {
      kind: "ok",
      value: {
        kind: "assessed",
        packageComplete: true,
        checksumsValid: true,
        requiredAssetsPresent: true,
        appShellAvailable: false,
        offlineReady: false,
      },
    },
  });
});

test("missing, wrong size and wrong checksum never become complete", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = await h.open();
      const signal = new AbortController().signal;
      return {
        missing: await store.read("../音源/one", signal),
        size: await store.save("../音源/one", new Blob(["bad"]), signal),
        checksum: await store.save(
          "../音源/one",
          new Blob([new Uint8Array(h.first.size)]),
          signal,
        ),
        readiness: await store.assess([1, 0, 0], signal),
      };
    }),
  ).toMatchObject({
    missing: { kind: "error", error: "missing" },
    size: { error: "corrupt" },
    checksum: { error: "corrupt" },
    readiness: {
      value: { packageComplete: false, missingAssetIds: ["../音源/one"] },
    },
  });
});

test("stored corruption is observed from disk, rejected on read, and repairable", async ({
  page,
}) => {
  const result = await page.evaluate(async () => {
    const h = window.storageHarness;
    const store = await h.open();
    const signal = new AbortController().signal;
    await store.save("../音源/one", h.first, signal);
    const root = await (
      await navigator.storage.getDirectory()
    ).getDirectoryHandle("abservice-assets-v1");
    const iterator = (
      root as FileSystemDirectoryHandle & {
        values: () => AsyncIterableIterator<FileSystemFileHandle>;
      }
    ).values();
    const file = (await iterator.next()).value as FileSystemFileHandle;
    const writer = await file.createWritable();
    await writer.write(new Uint8Array(h.first.size));
    await writer.close();
    const read = await store.read("../音源/one", signal);
    const observed = await store.inspect(signal);
    const readiness = await store.assess([1, 0, 0], signal);
    const repair = await store.save("../音源/one", h.first, signal);
    return {
      read,
      actualHash:
        observed.kind === "ok"
          ? observed.value.inventory[0]?.checksum.value
          : "",
      zeroHash: await h.digest(new Blob([new Uint8Array(h.first.size)])),
      readiness,
      repair,
      after: await store.assess([1, 0, 0], signal),
    };
  });
  expect(result.actualHash).toBe(result.zeroHash);
  expect(result).toMatchObject({
    read: { error: "corrupt" },
    readiness: {
      value: { packageComplete: false, corruptAssetIds: ["../音源/one"] },
    },
    repair: { kind: "ok" },
    after: { value: { packageComplete: true } },
  });
});

test("pre-aborted operations reject arbitrary reasons without publishing data", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = await h.open();
      const controller = new AbortController();
      controller.abort("cancelled by user");
      return Promise.all([
        store.save("../音源/one", h.first, controller.signal),
        store.read("../音源/one", controller.signal),
        store.inspect(controller.signal),
        store.assess([1, 0, 0], controller.signal),
      ]);
    }),
  ).toEqual(
    Array.from({ length: 4 }, () => ({ kind: "error", error: "aborted" })),
  );
});

test("abort after native write discards the temporary data and permits retry", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = await h.open();
      const controller = new AbortController();
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- Restore the original method; invoke with an explicit native receiver. */
      const nativeWrite = FileSystemWritableFileStream.prototype.write;
      FileSystemWritableFileStream.prototype.write = async function (data) {
        await nativeWrite.call(this, data);
        controller.abort();
      };
      const cancelled = await store
        .save("../音源/one", h.first, controller.signal)
        .finally(() => {
          FileSystemWritableFileStream.prototype.write = nativeWrite;
        });
      const readiness = await store.assess(
        [1, 0, 0],
        new AbortController().signal,
      );
      const retry = await store.save(
        "../音源/one",
        h.first,
        new AbortController().signal,
      );
      return { cancelled, readiness, retry };
    }),
  ).toMatchObject({
    cancelled: { error: "aborted" },
    readiness: { value: { packageComplete: false } },
    retry: { kind: "ok" },
  });
});

test("quota estimate and quota error during write are distinguished from success; old generation survives", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = await h.open();
      const next = await h.open(h.second);
      const signal = new AbortController().signal;
      await store.save("../音源/one", h.first, signal);
      const estimate = navigator.storage.estimate.bind(navigator.storage);
      navigator.storage.estimate = () =>
        Promise.resolve({ usage: 100, quota: 100 });
      const preflight = await next
        .save("../音源/one", h.second, signal)
        .finally(() => {
          navigator.storage.estimate = estimate;
        });
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- Restore the original method; invoke with an explicit native receiver. */
      const nativeWrite = FileSystemWritableFileStream.prototype.write;
      FileSystemWritableFileStream.prototype.write = async function (data) {
        await nativeWrite.call(this, data);
        throw new DOMException(
          "injected disk quota failure",
          "QuotaExceededError",
        );
      };
      const failure = await next
        .save("../音源/one", h.second, signal)
        .finally(() => {
          FileSystemWritableFileStream.prototype.write = nativeWrite;
        });
      const old = await store.read("../音源/one", signal);
      const incomplete = await next.assess([1, 0, 0], signal);
      const retry = await next.save("../音源/one", h.second, signal);
      const newer = await next.read("../音源/one", signal);
      return {
        preflight,
        failure,
        oldMatches:
          old.kind === "ok" &&
          (await h.digest(old.value)) === (await h.digest(h.first)),
        incomplete,
        retry,
        newMatches:
          newer.kind === "ok" &&
          (await h.digest(newer.value)) === (await h.digest(h.second)),
      };
    }),
  ).toMatchObject({
    preflight: { error: "quota-exceeded" },
    failure: { error: "quota-exceeded" },
    oldMatches: true,
    incomplete: { value: { packageComplete: false } },
    retry: { kind: "ok" },
    newMatches: true,
  });
});

test("unavailable estimates and unsupported algorithms are not fabricated inventory", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const manifest = await h.manifest();
      const unsupported = h.createAssetStore({
        ...manifest,
        assets: manifest.assets.map((asset) => ({
          ...asset,
          checksum: { algorithm: "md5", value: "abc" },
        })),
      });
      const signal = new AbortController().signal;
      const algorithm =
        unsupported.kind === "ok"
          ? await unsupported.value.inspect(signal)
          : unsupported;
      const store = await h.open();
      const estimate = navigator.storage.estimate.bind(navigator.storage);
      navigator.storage.estimate = () => Promise.resolve({});
      const unknownCapacity = await store
        .assess([1, 0, 0], signal)
        .finally(() => {
          navigator.storage.estimate = estimate;
        });
      return {
        algorithm,
        unknownCapacity,
        unknownId: await store.read("other", signal),
      };
    }),
  ).toEqual({
    algorithm: { kind: "error", error: "unsupported-checksum" },
    unknownCapacity: { kind: "error", error: "storage-unavailable" },
    unknownId: { kind: "error", error: "unknown-asset" },
  });
});

test("cross-tab lock cancellation does not affect committed bytes", async ({
  page,
  context,
}) => {
  await page.evaluate(async () => {
    const h = window.storageHarness;
    await (
      await h.open()
    ).save("../音源/one", h.first, new AbortController().signal);
  });
  const other = await context.newPage();
  await other.goto("/");
  await other.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
  await other.evaluate(() => {
    void navigator.locks.request(
      "abservice-assets-v1",
      () => new Promise<void>(() => undefined),
    );
  });
  await expect
    .poll(() =>
      other.evaluate(async () =>
        (await navigator.locks.query()).held?.some(
          (lock) => lock.name === "abservice-assets-v1",
        ),
      ),
    )
    .toBe(true);
  expect(
    await page.evaluate(async () => {
      const store = await window.storageHarness.open();
      const controller = new AbortController();
      const result = store.read("../音源/one", controller.signal);
      controller.abort("cancel lock wait");
      return result;
    }),
  ).toEqual({ kind: "error", error: "aborted" });
  await other.close();
  expect(
    await page.evaluate(async () =>
      (await window.storageHarness.open()).assess(
        [1, 0, 0],
        new AbortController().signal,
      ),
    ),
  ).toMatchObject({ kind: "ok", value: { packageComplete: true } });
});

test("cancellation after close begins does not report rollback of a committed asset", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = await h.open();
      const controller = new AbortController();
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- The native close is called with its stream receiver and restored afterward. */
      const nativeClose = FileSystemWritableFileStream.prototype.close;
      FileSystemWritableFileStream.prototype.close = async function () {
        await nativeClose.call(this);
        controller.abort();
      };
      const saved = await store
        .save("../音源/one", h.first, controller.signal)
        .finally(() => {
          FileSystemWritableFileStream.prototype.close = nativeClose;
        });
      return {
        saved,
        readiness: await store.assess([1, 0, 0], new AbortController().signal),
      };
    }),
  ).toMatchObject({
    saved: { kind: "ok" },
    readiness: { value: { packageComplete: true } },
  });
});
