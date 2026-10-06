/* eslint-disable functional/immutable-data -- Browser-only observations of uploaded atlas and GPU lifecycle. */
import { expect, test } from "@playwright/test";
import { distribute, prepareAndReload } from "./support";

type SceneProbe = {
  titles: string[];
  uploads: number;
  artworkSamples: number[][];
  devices: GPUDevice[];
  errors: string[];
};
type Scope = typeof globalThis & { sceneProbe: SceneProbe };

test("代表負荷: 実PWAの表示素材・縦横・描画予算・pauseと再選択", async ({
  page,
  request,
}, testInfo) => {
  await page.addInitScript(() => {
    const probe: SceneProbe = {
      titles: [],
      uploads: 0,
      artworkSamples: [],
      devices: [],
      errors: [],
    };
    (globalThis as Scope).sceneProbe = probe;
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Called with the native canvas receiver. */
    const fill = CanvasRenderingContext2D.prototype.fillText;
    CanvasRenderingContext2D.prototype.fillText = function (...args) {
      probe.titles.push(args[0]);
      fill.apply(this, args);
    };
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Called with the native queue receiver. */
    const copy = GPUQueue.prototype.copyExternalImageToTexture;
    GPUQueue.prototype.copyExternalImageToTexture = function (...args) {
      probe.uploads += 1;
      const source = args[0].source;
      const pixel =
        source instanceof HTMLCanvasElement
          ? source.getContext("2d")?.getImageData(0, 0, 1, 1).data
          : undefined;
      probe.artworkSamples.push(Array.from(pixel ?? []));
      copy.apply(this, args);
    };
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Called with the native adapter receiver. */
    const create = GPUAdapter.prototype.requestDevice;
    GPUAdapter.prototype.requestDevice = async function (...args) {
      const device = await create.apply(this, args);
      device.addEventListener("uncapturederror", (event) =>
        probe.errors.push(event.error.message),
      );
      probe.devices.push(device);
      return device;
    };
  });
  await distribute(request, "v1");
  await prepareAndReload(page);
  await page.goto("/offline-player/#probe&load=scene&scale=0.5&fps=30");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-scene-load", "true");
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await page.locator("#play").click();
  await expect(page.locator("#visualizer-status")).toHaveText(
    "音に合わせて描画します",
  );
  await expect(page.locator("#probe")).toContainText("scene representative-v1");
  const albumTitle = await page.locator("#album-title").textContent();
  await expect
    .poll(() => page.evaluate(() => (globalThis as Scope).sceneProbe.titles))
    .toContain(albumTitle);
  await expect
    .poll(() => page.evaluate(() => (globalThis as Scope).sceneProbe.uploads))
    .toBeGreaterThan(0);
  for (const viewport of [
    { width: 1100, height: 850 },
    { width: 800, height: 1100 },
  ]) {
    await page.setViewportSize(viewport);
    await expect
      .poll(() =>
        page.locator("#visualizer").evaluate((canvas: HTMLCanvasElement) => ({
          width: canvas.width,
          height: canvas.height,
        })),
      )
      .toEqual({ width: viewport.width / 2, height: viewport.height / 2 });
    const box = await page.locator("#visualizer").boundingBox();
    expect(box).toEqual({ x: 0, y: 0, ...viewport });
    await expect(page.locator("#pause")).toBeEnabled();
    await page.screenshot({
      path: testInfo.outputPath(`scene-${String(viewport.width)}.png`),
    });
  }
  await page.locator("#pause").click();
  await expect(page.locator("#visualizer")).toHaveAttribute(
    "data-presentation-state",
    "paused",
  );
  await expect
    .poll(() =>
      page.locator("#visualizer").evaluate((c) => c.style.backgroundImage),
    )
    .not.toBe("");
  await page.locator("#stop").click();
  await expect(page.locator("#visualizer")).toHaveAttribute(
    "data-presentation-state",
    "selected",
  );
  await expect(page.locator("#visualizer")).toHaveCSS(
    "background-image",
    "none",
  );
  const before = await page.evaluate(
    () => (globalThis as Scope).sceneProbe.uploads,
  );
  await page.getByRole("button", { name: "Return", exact: true }).click();
  await page.locator("#play").click();
  await expect
    .poll(() => page.evaluate(() => (globalThis as Scope).sceneProbe.uploads))
    .toBeGreaterThan(before);
  await expect
    .poll(() => page.evaluate(() => (globalThis as Scope).sceneProbe.titles))
    .toContain("Evening Session");
  const samples = await page.evaluate(
    () => (globalThis as Scope).sceneProbe.artworkSamples,
  );
  expect(samples.at(-1)).not.toEqual(samples[0]);
  expect(
    await page.evaluate(() => (globalThis as Scope).sceneProbe.errors),
  ).toEqual([]);
});
