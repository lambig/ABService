/* eslint-disable functional/immutable-data -- Observe the actual geometry upload and GPU errors without substituting musical input or drawing commands. */
import { expect, test } from "@playwright/test";
import { manifest } from "player-study/fixture";
import { distribute, prepareAndReload } from "./support";

type Probe = {
  vertices: number[];
  errors: string[];
  draws: number;
  pixels: number[];
};
type Scope = typeof globalThis & { motionProbe: Probe };

test("旋律のドットは右端に留まり、過去の旋律とモチーフが左へ流れる", async ({
  page,
  request,
}) => {
  test.setTimeout(60000);
  await page.addInitScript(() => {
    const probe: Probe = { vertices: [], errors: [], draws: 0, pixels: [] };
    (globalThis as Scope).motionProbe = probe;
    const copy = document.createElement("canvas");
    copy.width = 390;
    copy.height = 300;
    const context = copy.getContext("2d");
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Preserve native receivers. */
    const write = GPUQueue.prototype.writeBuffer;
    GPUQueue.prototype.writeBuffer = function (buffer, offset, data, ...rest) {
      (buffer.usage & GPUBufferUsage.VERTEX && data instanceof Float32Array
        ? () => {
            probe.vertices = Array.from(data);
          }
        : () => undefined)();
      write.call(this, buffer, offset, data, ...rest);
    };
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Preserve native receivers. */
    const submit = GPUQueue.prototype.submit;
    GPUQueue.prototype.submit = function (commands) {
      submit.call(this, commands);
      const canvas = document.querySelector<HTMLCanvasElement>("#visualizer");
      (canvas !== null && context !== null
        ? () => {
            context.drawImage(canvas, 0, 0, copy.width, copy.height);
            probe.pixels = Array.from(
              context.getImageData(0, 0, copy.width, copy.height).data,
            );
            probe.draws += 1;
          }
        : () => undefined)();
    };
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Preserve native receivers. */
    const create = GPUAdapter.prototype.requestDevice;
    GPUAdapter.prototype.requestDevice = async function (...args) {
      const device = await create.apply(this, args);
      device.addEventListener("uncapturederror", (event) =>
        probe.errors.push(event.error.message),
      );
      return device;
    };
  });
  await distribute(request, "v1");
  await prepareAndReload(page);
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  const duration = Number(await page.locator("#seek").getAttribute("max"));
  await page.locator("#management-panel").evaluate((node) => {
    node.removeAttribute("hidden");
  });
  await page.locator("#musical-analysis-file").setInputFiles({
    name: "synthetic-analysis.json",
    mimeType: "application/json",
    buffer: Buffer.from(
      JSON.stringify({
        schemaVersion: 1,
        audioSha256: manifest.assets[0]?.checksum.value,
        durationSeconds: duration,
        analysisVersion: "a".repeat(64),
        engines: { melody: "b".repeat(64), harmony: "c".repeat(64) },
        melody: [
          { start: 0, end: 1, midi: 60, salience: 1 },
          { start: 1, end: duration, midi: 76, salience: 1 },
        ],
        harmony: [
          { start: 0, end: duration, root: 2, quality: "sus4", bass: null },
        ],
      }),
    ),
  });
  await expect(page.locator("#musical-analysis-import-status")).toHaveText(
    "主旋律・和音の解析を保存しました。",
  );
  await page.locator("#play").click();
  await expect(page.locator("#visualizer-status")).toHaveAttribute(
    "data-renderer-state",
    "running",
  );
  const read = () =>
    page.evaluate(() => {
      const p = (globalThis as Scope).motionProbe;
      const dot = p.vertices.findIndex((v, i) => i % 6 === 5 && v === 1);
      return {
        firstX: p.vertices[0],
        dotX: p.vertices[dot - 5],
        dotY: p.vertices[dot - 4],
        draws: p.draws,
      };
    });
  await expect.poll(async () => (await read()).draws).toBeGreaterThan(3);
  const first = await read();
  await expect
    .poll(async () => (await read()).firstX)
    .toBeLessThan((first.firstX ?? 0) - 0.08);
  await expect(page.locator("#visualizer")).toHaveAttribute(
    "data-melody-midi",
    "76",
  );
  const next = await read();
  expect(next.dotX).toBeCloseTo(0.8, 5);
  expect(first.dotX).toBeCloseTo(next.dotX ?? 0, 5);
  await expect
    .poll(async () => (await read()).dotY)
    .toBeCloseTo(-0.04 + ((4 / 11) * 2 - 1) * 0.6, 4);
  const colorCount = await page.evaluate(
    () => new Set((globalThis as Scope).motionProbe.pixels).size,
  );
  expect(colorCount).toBeGreaterThan(8);
  await page.locator("#management-panel").evaluate((node) => {
    node.removeAttribute("hidden");
  });
  await page.locator("#exhibition-menu").evaluate((node) => {
    node.setAttribute("open", "");
  });
  await page.locator("#pause").click();
  await expect(page.locator("#visualizer")).toHaveCSS(
    "background-image",
    /^url\(/,
  );
  const paused = await read();
  await page.waitForTimeout(300);
  expect(await read()).toEqual(paused);
  expect(
    await page.evaluate(() => (globalThis as Scope).motionProbe.errors),
  ).toEqual([]);
});
