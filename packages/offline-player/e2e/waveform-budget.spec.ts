/* eslint-disable functional/immutable-data -- Browser probes count asset body reads and overview decoding. */
import { expect, test } from "@playwright/test";
import { distribute, prepareAndReload, openManagement } from "./support";

type SelectionProbe = { reads: number; decodes: number };
type Scope = typeof globalThis & { selectionProbe: SelectionProbe };

for (const cache of ["missing", "saved", "invalid", "oversized"] as const) {
  test(`selection with ${cache} waveform does not scan audio, including reselection and reload`, async ({
    page,
    request,
    context,
  }) => {
    await distribute(request, "v1");
    await prepareAndReload(page);
    await (cache === "missing"
      ? Promise.resolve()
      : page.evaluate(async (mode) => {
          const root = await navigator.storage.getDirectory();
          const assets = await root.getDirectoryHandle("abservice-assets-v1");
          const overviews = await root.getDirectoryHandle(
            "waveform-overviews-v1",
            {
              create: true,
            },
          );
          const iterator = (
            assets as FileSystemDirectoryHandle & {
              values: () => AsyncIterableIterator<FileSystemFileHandle>;
            }
          ).values();
          for await (const handle of iterator) {
            const file = await handle.getFile();
            const checksum = Array.from(
              new Uint8Array(
                await crypto.subtle.digest("SHA-256", await file.arrayBuffer()),
              ),
              (n) => n.toString(16).padStart(2, "0"),
            ).join("");
            const writer = await (
              await overviews.getFileHandle(`${checksum}.json`, {
                create: true,
              })
            ).createWritable();
            await writer.write(
              mode === "saved"
                ? JSON.stringify({
                    duration: 8,
                    peaks: Array.from({ length: 256 }, () => 0.5),
                  })
                : mode === "invalid"
                  ? "invalid json"
                  : " ".repeat(16385),
            );
            await writer.close();
          }
        }, cache));
    await page.addInitScript(() => {
      const probe: SelectionProbe = { reads: 0, decodes: 0 };
      (globalThis as Scope).selectionProbe = probe;
      const bodies = new WeakSet<Blob>();
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- Native receivers are preserved. */
      const slice = Blob.prototype.slice;
      /* eslint-disable-next-line @typescript-eslint/unbound-method -- Native receivers are preserved. */
      const arrayBuffer = Blob.prototype.arrayBuffer;
      Blob.prototype.slice = function (...args) {
        const part = slice.apply(this, args);
        ([
          bodies.has(this),
          this instanceof File && /^[a-f0-9]{64}$/.test(this.name),
        ].some(Boolean)
          ? () => {
              bodies.add(part);
            }
          : () => undefined)();
        return part;
      };
      Blob.prototype.arrayBuffer = function () {
        probe.reads += Number(
          [
            bodies.has(this),
            this instanceof File && /^[a-f0-9]{64}$/.test(this.name),
          ].some(Boolean),
        );
        return arrayBuffer.call(this);
      };
      const Native = OfflineAudioContext;
      globalThis.OfflineAudioContext = new Proxy(Native, {
        construct(target, args: ConstructorParameters<typeof Native>) {
          probe.decodes += 1;
          return Reflect.construct(target, args);
        },
      });
    });
    await context.setOffline(true);
    for (const restart of [false, true]) {
      await page.reload();
      for (const title of ["Album crossfade", "Air study", "Album crossfade"]) {
        await page.getByRole("button", { name: title, exact: true }).click();
        await expect(page.locator("#play-status")).toHaveText(
          /再生できます|再生中/u,
        );
        await expect(page.locator("#waveform-status")).toHaveText(
          cache === "saved" ? "" : "再生位置をタップで選べます",
        );
        await (cache === "saved"
          ? expect(page.locator(".waveform-path").first()).not.toHaveAttribute(
              "d",
              "",
            )
          : expect(page.locator(".waveform-path").first()).toHaveAttribute(
              "d",
              "",
            ));
        await ((await page.locator("#play").isVisible())
          ? page.locator("#play").click()
          : Promise.resolve());
        await expect(page.locator("#play-status")).toHaveText("再生中");
        await page.locator("#seek").fill("3");
        await expect
          .poll(() => page.locator("#seek").inputValue().then(Number))
          .toBeGreaterThanOrEqual(3);
        await expect(page.locator("#error")).toBeEmpty();
      }
      expect(
        await page.evaluate(() => (globalThis as Scope).selectionProbe),
        `restart=${String(restart)}`,
      ).toEqual({ reads: 0, decodes: 0 });
    }
  });
}

for (const viewport of [
  { width: 1100, height: 850 },
  { width: 800, height: 1100 },
]) {
  test(`代表負荷の管理操作は通常PIN経路で開く ${String(viewport.width)}`, async ({
    page,
    request,
  }) => {
    await distribute(request, "v1");
    await prepareAndReload(page);
    await page.goto("/offline-player/#probe&load=scene&scale=0.5&fps=30");
    await page.reload();
    await page.setViewportSize(viewport);
    await expect(page.locator("html")).toHaveAttribute(
      "data-scene-load",
      "true",
    );
    await page.getByRole("button", { name: "Reel study", exact: true }).click();
    await page.locator("#play").click();
    await expect(page.locator("#play-status")).toHaveText("再生中");
    await expect(page.locator("#management-unlock")).toBeInViewport();
    await expect(page.locator("#pause")).toBeHidden();
    await openManagement(page);
    await page.locator("#exhibition-menu > summary").click();
    await page.locator("#pause").click();
    await expect(page.locator("#play-status")).toHaveText("一時停止");
    await expect(page.locator("#management-panel")).toBeHidden();
    await openManagement(page);
    await page.locator("#exhibition-menu > summary").click();
    await page.locator("#stop").click();
    await expect(page.locator("#play-status")).toHaveText("停止中");
  });
}
