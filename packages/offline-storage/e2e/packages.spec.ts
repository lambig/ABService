/* eslint-disable functional/immutable-data -- Browser failure injection temporarily replaces native methods and restores them in finally blocks. */
import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
});

test("a staged v3 package survives reload as pending and becomes active only on promote", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      return {
        empty: await store.read("active", signal),
        staged: await store.stage(h.v3, signal),
        active: await store.read("active", signal),
      };
    }),
  ).toEqual({
    empty: { kind: "ok", value: undefined },
    staged: { kind: "ok", value: undefined },
    active: { kind: "ok", value: undefined },
  });
  await page.reload();
  await page.waitForFunction(
    () => typeof window.storageHarness !== "undefined",
  );
  const result = await page.evaluate(async () => {
    const h = window.storageHarness;
    const store = h.createPackageStore();
    const signal = new AbortController().signal;
    const pending = await store.read("pending", signal);
    const promoted = await store.promote(signal);
    return {
      pending,
      promoted,
      active: await store.read("active", signal),
      after: await store.read("pending", signal),
      listed: await store.list(signal),
      expected: h.v3,
    };
  });
  expect(result.pending).toEqual({ kind: "ok", value: result.expected });
  expect(result).toMatchObject({
    promoted: { kind: "ok" },
    active: { kind: "ok", value: result.expected },
    after: { kind: "ok", value: undefined },
    listed: {
      kind: "ok",
      value: {
        packages: [
          {
            packageVersion: result.expected.packageVersion,
            slots: ["active"],
          },
        ],
        unreadable: 0,
      },
    },
  });
});

test("a new pending generation leaves active untouched; discarding it keeps the file", async ({
  page,
}) => {
  const result = await page.evaluate(async () => {
    const h = window.storageHarness;
    const store = h.createPackageStore();
    const signal = new AbortController().signal;
    await store.stage(h.generation("old"), signal);
    await store.promote(signal);
    const staged = await store.stage(h.generation("new", "新しい作品"), signal);
    const both = await store.list(signal);
    const active = await store.read("active", signal);
    const pending = await store.read("pending", signal);
    const discarded = await store.discardPending(signal);
    return {
      staged,
      both,
      active: active.kind === "ok" ? active.value?.packageVersion : active,
      pending: pending.kind === "ok" ? pending.value?.packageVersion : pending,
      discarded,
      afterPending: await store.read("pending", signal),
      afterList: await store.list(signal),
      noPending: await store.promote(signal),
    };
  });
  expect(result).toMatchObject({
    staged: { kind: "ok" },
    active: "old",
    pending: "new",
    discarded: { kind: "ok" },
    afterPending: { kind: "ok", value: undefined },
    noPending: { kind: "error", error: "missing" },
  });
  expect(result.both).toMatchObject({ kind: "ok" });
  const both = result.both.kind === "ok" ? result.both.value.packages : [];
  expect(both).toHaveLength(2);
  expect(both).toEqual(
    expect.arrayContaining([
      { packageVersion: "old", slots: ["active"] },
      { packageVersion: "new", slots: ["pending"] },
    ]),
  );
  const after =
    result.afterList.kind === "ok" ? result.afterList.value.packages : [];
  expect(after).toHaveLength(2);
  expect(after).toEqual(
    expect.arrayContaining([
      { packageVersion: "old", slots: ["active"] },
      { packageVersion: "new", slots: [] },
    ]),
  );
});

test("an invalid manifest is rejected before anything is written", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      return {
        unknown: await store.stage(
          {
            ...h.v3,
            albums: h.v3.albums.map((album) => ({ ...album, scene: "x" })),
          },
          signal,
        ),
        unsupported: await store.stage({ ...h.v3, schemaVersion: 99 }, signal),
        pending: await store.read("pending", signal),
        listed: await store.list(signal),
      };
    }),
  ).toEqual({
    unknown: { kind: "error", error: "invalid-manifest" },
    unsupported: { kind: "error", error: "invalid-manifest" },
    pending: { kind: "ok", value: undefined },
    listed: { kind: "ok", value: { packages: [], unreadable: 0 } },
  });
});

test("stored damage is reported as corrupt or missing, never returned as a package", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      await store.stage(h.generation("active"), signal);
      await store.promote(signal);
      const read = () => store.read("active", signal);
      await h.tamper("active", "{");
      const broken = await read();
      await h.tamper(
        "active",
        JSON.stringify({
          ...h.v3,
          packageVersion: "active",
          albums: h.v3.albums.map((album) => ({ ...album, scene: "x" })),
        }),
      );
      const unknownField = await read();
      await h.tamper("active", JSON.stringify(h.generation("other")));
      const otherVersion = await read();
      const listed = await store.list(signal);
      await h.erase("active");
      const erased = await read();
      await h.tamperPointer(JSON.stringify({ active: 1 }));
      return {
        broken,
        unknownField,
        otherVersion,
        listed,
        erased,
        pointer: await read(),
        stageOnBrokenPointer: await store.stage(h.v3, signal),
      };
    }),
  ).toEqual({
    broken: { kind: "error", error: "corrupt" },
    unknownField: { kind: "error", error: "corrupt" },
    otherVersion: { kind: "error", error: "corrupt" },
    listed: { kind: "ok", value: { packages: [], unreadable: 1 } },
    erased: { kind: "error", error: "missing" },
    pointer: { kind: "error", error: "corrupt" },
    stageOnBrokenPointer: { kind: "error", error: "corrupt" },
  });
});

test("a failed pointer commit keeps both previous slots", async ({ page }) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      await store.stage(h.generation("old"), signal);
      await store.promote(signal);
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- The native close is called with its stream receiver and restored afterward. */
      const nativeClose = FileSystemWritableFileStream.prototype.close;
      const failing = async <T>(
        closes: number,
        action: () => Promise<T>,
      ): Promise<T> => {
        const remaining = { value: closes };
        FileSystemWritableFileStream.prototype.close = async function () {
          remaining.value -= 1;
          return remaining.value < 0
            ? Promise.reject(
                new DOMException("injected disk failure", "QuotaExceededError"),
              )
            : nativeClose.call(this);
        };
        return action().finally(() => {
          FileSystemWritableFileStream.prototype.close = nativeClose;
        });
      };
      const version = async (slot: "active" | "pending") => {
        const result = await store.read(slot, signal);
        return result.kind === "ok" ? result.value?.packageVersion : result;
      };
      /* The manifest file commits; the pointer commit that would name it fails. */
      const stage = await failing(1, () =>
        store.stage(h.generation("new"), signal),
      );
      const afterStage = {
        active: await version("active"),
        pending: await version("pending"),
      };
      await store.stage(h.generation("new"), signal);
      const promote = await failing(0, () => store.promote(signal));
      return {
        stage,
        afterStage,
        promote,
        afterPromote: {
          active: await version("active"),
          pending: await version("pending"),
        },
      };
    }),
  ).toEqual({
    stage: { kind: "error", error: "quota-exceeded" },
    afterStage: { active: "old", pending: undefined },
    promote: { kind: "error", error: "quota-exceeded" },
    afterPromote: { active: "old", pending: "new" },
  });
});

test("a failed first pointer commit leaves an empty store that can be staged again", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- The native close is called with its stream receiver and restored afterward. */
      const nativeClose = FileSystemWritableFileStream.prototype.close;
      const closes = { value: 0 };
      /* The manifest file commits; the first pointer file is created but its commit fails. */
      FileSystemWritableFileStream.prototype.close = async function () {
        closes.value += 1;
        return closes.value > 1
          ? Promise.reject(
              new DOMException("injected disk failure", "QuotaExceededError"),
            )
          : nativeClose.call(this);
      };
      const failed = await store.stage(h.v3, signal).finally(() => {
        FileSystemWritableFileStream.prototype.close = nativeClose;
      });
      const pointer = await (
        await (
          await (
            await navigator.storage.getDirectory()
          ).getDirectoryHandle("abservice-packages-v1")
        ).getFileHandle("pointer.json")
      ).getFile();
      const active = await store.read("active", signal);
      const pending = await store.read("pending", signal);
      const retried = await store.stage(h.v3, signal);
      const staged = await store.read("pending", signal);
      return {
        failed,
        pointerBytes: pointer.size,
        active,
        pending,
        retried,
        staged: staged.kind === "ok" ? staged.value?.packageVersion : staged,
        expected: h.v3.packageVersion,
      };
    }),
  ).toEqual({
    failed: { kind: "error", error: "quota-exceeded" },
    pointerBytes: 0,
    active: { kind: "ok", value: undefined },
    pending: { kind: "ok", value: undefined },
    retried: { kind: "ok", value: undefined },
    staged: "0000000000000000000000000000000000000000000000000000000000000000",
    expected:
      "0000000000000000000000000000000000000000000000000000000000000000",
  });
});

test("a referenced generation cannot be replaced by different content under the same version", async ({
  page,
}) => {
  const result = await page.evaluate(async () => {
    const h = window.storageHarness;
    const store = h.createPackageStore();
    const signal = new AbortController().signal;
    await store.stage(h.generation("same"), signal);
    await store.promote(signal);
    const conflict = await store.stage(
      h.generation("same", "別の作品"),
      signal,
    );
    const identical = await store.stage(h.generation("same"), signal);
    const active = await store.read("active", signal);
    await store.discardPending(signal);
    /* A damaged active generation is repaired by the same version. */
    await h.tamper("same", "{");
    const repaired = await store.stage(h.generation("same"), signal);
    return {
      conflict,
      identical,
      title: active.kind === "ok" ? active.value?.albums[0]?.title : active,
      repaired,
      readable: await store.read("active", signal),
      expected: h.generation("same"),
    };
  });
  expect(result).toEqual({
    conflict: { kind: "error", error: "conflict" },
    identical: { kind: "ok", value: undefined },
    title: "見本の作品",
    repaired: { kind: "ok", value: undefined },
    readable: { kind: "ok", value: result.expected },
    expected: result.expected,
  });
});

test("versions containing slashes, dots and Japanese are data, not paths", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      const version = "../世代/一.";
      const staged = await store.stage(h.generation(version), signal);
      const pending = await store.read("pending", signal);
      return {
        staged,
        pending:
          pending.kind === "ok" ? pending.value?.packageVersion : pending,
        listed: await store.list(signal),
      };
    }),
  ).toEqual({
    staged: { kind: "ok", value: undefined },
    pending: "../世代/一.",
    listed: {
      kind: "ok",
      value: {
        packages: [{ packageVersion: "../世代/一.", slots: ["pending"] }],
        unreadable: 0,
      },
    },
  });
});

test("collect removes only manifests no slot references, keeping a referenced file even when it is unreadable", async ({
  page,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      await store.stage(h.generation("old"), signal);
      await store.promote(signal);
      await store.stage(h.generation("abandoned"), signal);
      await store.stage(h.generation("new"), signal);
      const before = await h.storedManifests();
      await h.tamper("new", "{");
      const collected = await store.collect(signal);
      const listed = await store.list(signal);
      const repaired = await store.stage(h.generation("new"), signal);
      const pending = await store.read("pending", signal);
      await store.promote(signal);
      const afterPromote = await store.collect(signal);
      return {
        before,
        collected,
        listed,
        repaired,
        pending:
          pending.kind === "ok" ? pending.value?.packageVersion : pending,
        afterPromote,
        remaining: await h.storedManifests(),
        again: await store.collect(signal),
      };
    }),
  ).toEqual({
    before: 3,
    collected: { kind: "ok", value: 1 },
    listed: {
      kind: "ok",
      value: {
        packages: [{ packageVersion: "old", slots: ["active"] }],
        unreadable: 1,
      },
    },
    repaired: { kind: "ok", value: undefined },
    pending: "new",
    afterPromote: { kind: "ok", value: 1 },
    remaining: 1,
    again: { kind: "ok", value: 0 },
  });
});

test("collect removes nothing when the pointer is corrupt", async ({ page }) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const signal = new AbortController().signal;
      await store.stage(h.generation("old"), signal);
      await store.promote(signal);
      await store.stage(h.generation("new"), signal);
      await store.discardPending(signal);
      await h.tamperPointer("[]");
      return {
        collected: await store.collect(signal),
        remaining: await h.storedManifests(),
      };
    }),
  ).toEqual({
    collected: { kind: "error", error: "corrupt" },
    remaining: 2,
  });
});

test("pre-aborted operations and cancelled lock waits change nothing", async ({
  page,
  context,
}) => {
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const store = h.createPackageStore();
      const controller = new AbortController();
      controller.abort("cancelled by user");
      return Promise.all([
        store.stage(h.v3, controller.signal),
        store.read("active", controller.signal),
        store.promote(controller.signal),
        store.discardPending(controller.signal),
        store.list(controller.signal),
        store.collect(controller.signal),
      ]);
    }),
  ).toEqual(
    Array.from({ length: 6 }, () => ({ kind: "error", error: "aborted" })),
  );
  const other = await context.newPage();
  await other.goto("/");
  await other.evaluate(() => {
    void navigator.locks.request(
      "abservice-packages-v1",
      () => new Promise<void>(() => undefined),
    );
  });
  await expect
    .poll(() =>
      other.evaluate(async () =>
        (await navigator.locks.query()).held?.some(
          (lock) => lock.name === "abservice-packages-v1",
        ),
      ),
    )
    .toBe(true);
  expect(
    await page.evaluate(async () => {
      const h = window.storageHarness;
      const controller = new AbortController();
      const result = h.createPackageStore().stage(h.v3, controller.signal);
      controller.abort("cancel lock wait");
      return result;
    }),
  ).toEqual({ kind: "error", error: "aborted" });
  await other.close();
  expect(
    await page.evaluate(async () =>
      window.storageHarness
        .createPackageStore()
        .read("pending", new AbortController().signal),
    ),
  ).toEqual({ kind: "ok", value: undefined });
});
