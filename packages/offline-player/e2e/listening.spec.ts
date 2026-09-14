/* eslint-disable functional/immutable-data -- Test-only probes observe native Worklet messages, media contexts and actual GPU uniforms. */
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import type { AudioFeatures } from "abservice-audio-dsp";

type Probe = {
  contexts: AudioContext[];
  nodes: AudioWorkletNode[];
  features: AudioFeatures[];
  uniforms: number[][];
  errors: string[];
};
type Scope = typeof globalThis & { listeningProbe: Probe };
test.beforeEach(async ({ context }) => {
  await context.addInitScript(() => {
    const probe: Probe = {
      contexts: [],
      nodes: [],
      features: [],
      uniforms: [],
      errors: [],
    };
    (globalThis as Scope).listeningProbe = probe;
    const NativeContext = AudioContext;
    globalThis.AudioContext = class extends NativeContext {
      constructor(...args: ConstructorParameters<typeof NativeContext>) {
        super(...args);
        probe.contexts.push(this);
      }
    };
    const NativeNode = AudioWorkletNode;
    globalThis.AudioWorkletNode = class extends NativeNode {
      constructor(...args: ConstructorParameters<typeof NativeNode>) {
        super(...args);
        probe.nodes.push(this);
        this.port.addEventListener(
          "message",
          (event: MessageEvent<{ features: AudioFeatures }>) => {
            probe.features.push(event.data.features);
          },
        );
      }
    };
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Native method is invoked with its actual GPUQueue receiver. */
    const write = GPUQueue.prototype.writeBuffer;
    GPUQueue.prototype.writeBuffer = function (...args) {
      const data = args[2];
      (data instanceof Float32Array
        ? () => {
            probe.uniforms.push(Array.from(data));
          }
        : () => undefined)();
      write.apply(this, args);
    };
    addEventListener("error", (event) => {
      probe.errors.push(event.message);
    });
    addEventListener("unhandledrejection", (event) => {
      probe.errors.push(String(event.reason));
    });
  });
});
const observe = (page: Page) =>
  page.evaluate(() => {
    const probe = (globalThis as Scope).listeningProbe;
    return {
      features: probe.features,
      uniforms: probe.uniforms,
      contexts: probe.contexts.map((context) => context.state),
      errors: probe.errors,
    };
  });
const selectAndPlay = async (
  page: Page,
  title = "Reel study",
): Promise<void> => {
  await page.getByRole("button", { name: title, exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
};
const prepare = async (page: Page): Promise<void> => {
  await page.goto("/offline-player/");
  await expect(page.locator("#prepare")).toBeEnabled();
  await page.locator("#prepare").click();
  await expect(page.locator("#readiness")).toHaveText(
    "オフライン再生の準備ができました",
  );
};

test("オフライン新ページで実FLACの特徴量がWebGPU描画を駆動する", async ({
  page,
  context,
}) => {
  await prepare(page);
  await page.close();
  await context.setOffline(true);
  const next = await context.newPage();
  expect((await next.goto("/offline-player/"))?.fromServiceWorker()).toBe(true);
  await selectAndPlay(next);
  await expect
    .poll(async () =>
      (await observe(next)).features.some(
        (frame) => frame.rms > 0.01 && frame.midEnergy > 0,
      ),
    )
    .toBe(true);
  await expect
    .poll(async () =>
      (await observe(next)).uniforms.some(
        (uniform) => (uniform[3] ?? 0) > 1 && (uniform[9] ?? 0) > 0.65,
      ),
    )
    .toBe(true);
  await expect(next.locator("#analysis-status")).toBeEmpty();
  await next.screenshot({
    path: "test-results/listening-playing.png",
    fullPage: true,
  });
  await next.locator("#pause").click();
  await next.waitForTimeout(200);
  const paused = await observe(next);
  await next.waitForTimeout(250);
  expect((await observe(next)).uniforms.length).toBe(paused.uniforms.length);
  await next.locator("#seek").fill("2");
  await next.locator("#play").click();
  await expect
    .poll(async () => (await observe(next)).uniforms.length)
    .toBeGreaterThan(paused.uniforms.length);
  await next.setViewportSize({ width: 420, height: 860 });
  await next.screenshot({
    path: "test-results/listening-portrait.png",
    fullPage: true,
  });
  expect(
    await next.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await next.locator("#stop").click();
  await expect
    .poll(async () =>
      (await observe(next)).contexts.every((state) => state === "closed"),
    )
    .toBe(true);
  expect((await observe(next)).errors).toEqual([]);
});

test("選曲で旧graphを解放し、旧通知やページ破棄後に描画を復活させない", async ({
  page,
}) => {
  await prepare(page);
  await selectAndPlay(page);
  await expect
    .poll(async () => (await observe(page)).features.length)
    .toBeGreaterThan(2);
  await selectAndPlay(page, "Air study");
  await expect
    .poll(async () => (await observe(page)).contexts[0])
    .toBe("closed");
  await expect.poll(async () => (await observe(page)).contexts.length).toBe(2);
  await page.evaluate(() => {
    dispatchEvent(new Event("pagehide"));
  });
  await expect
    .poll(async () =>
      (await observe(page)).contexts.every((state) => state === "closed"),
    )
    .toBe(true);
  const count = (await observe(page)).uniforms.length;
  await page.waitForTimeout(250);
  expect((await observe(page)).uniforms).toHaveLength(count);
  expect((await observe(page)).errors).toEqual([]);
});

test("Worklet取得失敗でも音源の再生・シーク・停止は維持する", async ({
  page,
}) => {
  await page.addInitScript(() => {
    AudioWorklet.prototype.addModule = () =>
      Promise.reject(new Error("test module failure"));
  });
  await prepare(page);
  await selectAndPlay(page);
  await expect(page.locator("#analysis-status")).toContainText(
    "音響解析を利用できません",
  );
  await expect
    .poll(() => page.locator("#seek").inputValue().then(Number))
    .toBeGreaterThan(0.5);
  await page.locator("#seek").fill("3");
  await expect(page.locator("#position")).toHaveText("0:03");
  await page.locator("#stop").click();
  await expect
    .poll(async () =>
      (await observe(page)).contexts.every((state) => state === "closed"),
    )
    .toBe(true);
  expect((await observe(page)).errors).toEqual([]);
});

test("WebGPU未対応でも音響解析と再生操作を維持する", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "gpu", { value: undefined });
  });
  await prepare(page);
  await selectAndPlay(page);
  await expect(page.locator("#visualizer-status")).toContainText(
    "描画を利用できません",
  );
  await expect
    .poll(async () => (await observe(page)).features.length)
    .toBeGreaterThan(2);
  await page.locator("#pause").click();
  await expect(page.locator("#play-status")).toHaveText("一時停止");
  expect((await observe(page)).errors).toEqual([]);
});

test("Worklet初期化中の停止で遅い完了を捨て、次の再生は開始できる", async ({
  page,
}) => {
  await page.addInitScript(() => {
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Native addModule is called with its AudioWorklet receiver after a test-only gate. */
    const original = AudioWorklet.prototype.addModule;
    const gate: { first: boolean; release: (() => void) | null } = {
      first: true,
      release: null,
    };
    const pending = new Promise<void>((resolve) => {
      gate.release = resolve;
    });
    (
      globalThis as typeof globalThis & { releaseAnalysis: () => void }
    ).releaseAnalysis = () => {
      gate.release?.();
    };
    AudioWorklet.prototype.addModule = function (...args) {
      const first = gate.first;
      gate.first = false;
      return first
        ? pending.then(() => original.apply(this, args))
        : original.apply(this, args);
    };
  });
  await prepare(page);
  await selectAndPlay(page);
  await page.locator("#stop").click();
  await page.evaluate(() => {
    (
      globalThis as typeof globalThis & { releaseAnalysis: () => void }
    ).releaseAnalysis();
  });
  await expect
    .poll(async () => (await observe(page)).contexts[0])
    .toBe("closed");
  await selectAndPlay(page, "Air study");
  await expect
    .poll(async () => (await observe(page)).features.length)
    .toBeGreaterThan(2);
  expect((await observe(page)).errors).toEqual([]);
});
