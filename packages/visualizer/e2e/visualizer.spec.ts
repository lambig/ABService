/* eslint-disable functional/immutable-data -- Test instrumentation observes real GPU submissions and resource disposal; canvas pixels are decoded in a separate test canvas. */
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

type Observation = {
  submissions: number;
  destroyed: number;
  devices: number;
  errors: string[];
};
type Scope = typeof globalThis & { gpuObservation: Observation };

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const observed: Observation = {
      submissions: 0,
      destroyed: 0,
      devices: 0,
      errors: [],
    };
    (globalThis as Scope).gpuObservation = observed;
    globalThis.addEventListener("error", (event) =>
      observed.errors.push(event.message),
    );
    globalThis.addEventListener("unhandledrejection", (event) =>
      observed.errors.push(String(event.reason)),
    );
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- The original prototype method is invoked with its actual adapter via call below. */
    const request = GPUAdapter.prototype.requestDevice;
    GPUAdapter.prototype.requestDevice = async function (descriptor) {
      const device = await request.call(this, descriptor);
      observed.devices += 1;
      device.addEventListener("uncapturederror", (event) =>
        observed.errors.push(event.error.message),
      );
      const submit = device.queue.submit.bind(device.queue);
      device.queue.submit = (commands) => {
        submit(commands);
        observed.submissions += 1;
      };
      const destroy = device.destroy.bind(device);
      device.destroy = () => {
        destroy();
        observed.destroyed += 1;
      };
      return device;
    };
  });
});

const observation = (page: Page): Promise<Observation> =>
  page.evaluate(() => (globalThis as Scope).gpuObservation);

const start = async (page: Page): Promise<void> => {
  const before = (await observation(page)).submissions;
  await page.getByRole("button", { name: "先頭から開始", exact: true }).click();
  await expect(page.locator("#status")).toHaveText(
    "実行中 · fake AudioFeatures",
  );
  await expect
    .poll(async () => (await observation(page)).submissions)
    .toBeGreaterThan(before + 3);
};

const painted = async (page: Page): Promise<void> => {
  const png = await page.locator("#scene").screenshot();
  const pixels = await page.evaluate(async (base64) => {
    const image = new Image();
    image.src = `data:image/png;base64,${base64}`;
    await image.decode();
    const canvas = document.createElement("canvas");
    canvas.width = 64;
    canvas.height = 64;
    const context = canvas.getContext("2d");
    context?.drawImage(image, 0, 0, 64, 64);
    const rgba =
      context?.getImageData(0, 0, 64, 64).data ?? new Uint8ClampedArray();
    const rgb = Array.from({ length: 4096 }, (_, index) =>
      Array.from(rgba.slice(index * 4, index * 4 + 3)),
    );
    return {
      colors: new Set(rgb.map((pixel) => pixel.join(","))).size,
      bright: rgb.filter((pixel) => pixel.some((value) => value > 120)).length,
    };
  }, png.toString("base64"));
  expect(pixels.colors).toBeGreaterThan(64);
  expect(pixels.bright).toBeGreaterThan(50);
  expect((await observation(page)).errors).toEqual([]);
};

test("WGSLで描画し、停止で送信と更新が止まり、再開始できる", async ({
  page,
}) => {
  await page.goto("/");
  await start(page);
  await painted(page);
  await page.screenshot({ path: "test-results/visualizer-playing.png" });
  await page.getByRole("button", { name: "停止", exact: true }).click();
  await expect(page.locator("#status")).toHaveText("停止しました。");
  const stopped = await observation(page);
  const metrics = await page.locator("#metrics").textContent();
  await page.waitForTimeout(300);
  expect(await observation(page)).toEqual(stopped);
  expect(stopped.destroyed).toBe(stopped.devices);
  expect(await page.locator("#metrics").textContent()).toBe(metrics);
  await start(page);
  await painted(page);
  expect((await observation(page)).devices).toBe(2);
});

test("縦横のサイズ変更とfullscreen復帰後も描画する", async ({ page }) => {
  await page.goto("/");
  await start(page);
  const resized = async (): Promise<void> => {
    await expect
      .poll(() =>
        page.locator("#scene").evaluate((node) => {
          const canvas = node as HTMLCanvasElement;
          return (
            canvas.width ===
              Math.floor(canvas.clientWidth * Math.min(devicePixelRatio, 2)) &&
            canvas.height ===
              Math.floor(canvas.clientHeight * Math.min(devicePixelRatio, 2))
          );
        }),
      )
      .toBe(true);
    await painted(page);
  };
  await page.setViewportSize({ width: 480, height: 800 });
  await resized();
  await page.screenshot({ path: "test-results/visualizer-portrait.png" });
  await page.setViewportSize({ width: 960, height: 720 });
  await resized();
  await page.getByRole("button", { name: "全画面", exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => document.fullscreenElement?.id))
    .toBe("scene");
  await resized();
  await page.evaluate(() => document.exitFullscreen());
  await expect
    .poll(() => page.evaluate(() => document.fullscreenElement))
    .toBeNull();
  await resized();
});

test("実行中の再開始でも旧deviceを解放する", async ({ page }) => {
  await page.goto("/");
  await start(page);
  await start(page);
  await painted(page);
  expect((await observation(page)).destroyed).toBe(1);
  await page.getByRole("button", { name: "停止", exact: true }).click();
  const stopped = await observation(page);
  expect(stopped.destroyed).toBe(2);
  await page.waitForTimeout(300);
  expect(await observation(page)).toEqual(stopped);
});

test("WebGPU未対応を説明し、初期化中表示に留まらない", async ({ page }) => {
  await page.addInitScript(() => {
    Reflect.deleteProperty(Navigator.prototype, "gpu");
  });
  await page.goto("/");
  await page.getByRole("button", { name: "先頭から開始", exact: true }).click();
  await expect(page.locator("#status")).toContainText(
    "WebGPU unavailable。WebGPUが利用可能な環境で再試行してください。",
  );
  expect((await observation(page)).submissions).toBe(0);
  expect((await observation(page)).errors).toEqual([]);
});
