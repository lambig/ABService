/* eslint-disable functional/immutable-data -- Test-only probes observe native Worklet messages, media contexts and actual GPU uniforms. */
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import type { AudioFeatures } from "abservice-audio-dsp";
import {
  drawnWithTypeface,
  JAPANESE_SAMPLE,
  loadTypefaces,
} from "player-study-e2e/typeface";
import { distribute, prepareAndReload } from "./support";

type Probe = {
  contexts: AudioContext[];
  nodes: AudioWorkletNode[];
  features: AudioFeatures[];
  uniforms: number[][];
  errors: string[];
  devices: GPUDevice[];
  released: number;
  states: string[];
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
      devices: [],
      released: 0,
      states: [],
    };
    (globalThis as Scope).listeningProbe = probe;
    /* Every change of the presentation state is kept, including a seek that lasts only a moment. */
    new MutationObserver((records) => {
      records.forEach((record) => {
        const value = (record.target as HTMLElement).dataset[
          "presentationState"
        ];
        (value !== undefined && probe.states.at(-1) !== value
          ? () => {
              probe.states.push(value);
            }
          : () => undefined)();
      });
    }).observe(document, {
      subtree: true,
      attributes: true,
      attributeFilter: ["data-presentation-state"],
    });
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Native method is invoked with its actual adapter receiver. */
    const requestDevice = GPUAdapter.prototype.requestDevice;
    GPUAdapter.prototype.requestDevice = async function (...args) {
      const device = await requestDevice.apply(this, args);
      probe.devices.push(device);
      void device.lost.then(() => {
        probe.released += 1;
      });
      return device;
    };
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
const prepare = prepareAndReload;
test.beforeEach(async ({ request }) => {
  await distribute(request, "v1");
});

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

test("device lostから1回だけ描画を作り直し、再び失えば縮退して再生を続ける", async ({
  page,
}) => {
  await prepare(page);
  await selectAndPlay(page);
  await expect(page.locator("#visualizer-status")).toHaveText(
    "音に合わせて描画します",
  );
  const loseCurrent = () =>
    page.evaluate(() => {
      (globalThis as Scope).listeningProbe.devices.at(-1)?.destroy();
    });
  const devicesBefore = await page.evaluate(
    () => (globalThis as Scope).listeningProbe.devices.length,
  );
  await loseCurrent();
  /* The status reads running both before and after the recovery; the new device shows that it happened. */
  await expect
    .poll(() =>
      page.evaluate(() => (globalThis as Scope).listeningProbe.devices.length),
    )
    .toBe(devicesBefore + 1);
  await expect(page.locator("#visualizer-status")).toHaveText(
    "音に合わせて描画します",
  );
  const uniformsAfterRecovery = (await observe(page)).uniforms.length;
  await expect
    .poll(async () => (await observe(page)).uniforms.length)
    .toBeGreaterThan(uniformsAfterRecovery);
  await loseCurrent();
  await expect(page.locator("#visualizer-status")).toHaveText(
    "描画を利用できません。再生と操作は続けられます。",
  );
  /* No third renderer is attempted; audio analysis and playback carry on. */
  await page.waitForTimeout(300);
  expect(
    await page.evaluate(() => (globalThis as Scope).listeningProbe.devices.length),
  ).toBe(devicesBefore + 1);
  const featuresBefore = (await observe(page)).features.length;
  await expect
    .poll(async () => (await observe(page)).features.length)
    .toBeGreaterThan(featuresBefore);
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await page.locator("#pause").click();
  await expect(page.locator("#play-status")).toHaveText("一時停止");
  expect((await observe(page)).errors).toEqual([]);
});

test("一時停止でGPU資源を放し、再開で作り直して描画を続ける", async ({
  page,
}) => {
  const gpu = () =>
    page.evaluate(() => {
      const probe = (globalThis as Scope).listeningProbe;
      return { devices: probe.devices.length, released: probe.released };
    });
  await prepare(page);
  await selectAndPlay(page);
  await expect(page.locator("#visualizer-status")).toHaveText(
    "音に合わせて描画します",
  );
  const playing = await gpu();
  await page.locator("#pause").click();
  await expect(page.locator("#play-status")).toHaveText("一時停止");
  /* The last composition stays on screen as a still once the GPU is gone. */
  await expect(page.locator("#visualizer")).toHaveCSS(
    "background-image",
    /^url\("blob:/u,
  );
  /* Every device created so far is destroyed while paused; nothing is held for a long pause. */
  await expect.poll(async () => (await gpu()).released).toBe(playing.devices);
  const paused = (await observe(page)).uniforms.length;
  await page.waitForTimeout(250);
  expect((await observe(page)).uniforms).toHaveLength(paused);
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect.poll(async () => (await gpu()).devices).toBe(playing.devices + 1);
  await expect(page.locator("#visualizer-status")).toHaveText(
    "音に合わせて描画します",
  );
  await expect(page.locator("#visualizer")).toHaveCSS("background-image", "none");
  await expect
    .poll(async () => (await observe(page)).uniforms.length)
    .toBeGreaterThan(paused);
  expect((await observe(page)).errors).toEqual([]);
});

test("hashの負荷ノブで描画寸法とfpsを下げ、#probeでCPUとGPUを分けた観測と起動時の計測点を示す", async ({
  page,
}) => {
  await prepare(page);
  await expect(page.locator("#probe")).toBeHidden();
  /* A hash-only change is a same-document navigation; reload so that the budget is read again. */
  await page.goto("/offline-player/#probe&scale=0.5&fps=30");
  await page.reload();
  await selectAndPlay(page);
  await expect(page.locator("#probe")).toBeVisible();
  await expect(page.locator("#probe")).toContainText("budget dpr 2 scale 0.5");
  await expect(page.locator("#probe")).toContainText(/cycle cpu p50 [\d.]+/);
  await expect(page.locator("#probe")).toContainText(/renderer cpu p50 [\d.]+/);
  await expect(page.locator("#probe")).toContainText(/gpu \(submit→done\) p50 [\d.]+/);
  await expect(page.locator("#probe")).toContainText(/interval p50 [\d.]+/);
  await expect(page.locator("#probe")).toContainText("listening:script");
  await expect(page.locator("#probe")).toContainText("listening:ready");
  await expect(page.locator("#probe")).toContainText("listening:first-frame");
  await expect(page.locator("#probe")).toContainText(/skipped [1-9]/);
  const size = await page.locator("#visualizer").evaluate((canvas: HTMLCanvasElement) => ({
    width: canvas.width,
    expected: Math.floor(canvas.clientWidth * Math.min(devicePixelRatio, 2) * 0.5),
  }));
  expect(size.width).toBe(size.expected);
  expect((await observe(page)).errors).toEqual([]);
});

test("#probeで音声側の通知・Workletの平均負荷・音切れを示し、DSPベンチマークは再生と重ねずに走らせる", async ({
  page,
}) => {
  /* The worker's reply is held until the test releases it, so that the running state can be observed. */
  await page.addInitScript(() => {
    const gate: { release: () => void } = { release: () => undefined };
    const released = new Promise<void>((resolve) => {
      gate.release = resolve;
    });
    (
      globalThis as typeof globalThis & { releaseBenchmark: () => void }
    ).releaseBenchmark = () => {
      gate.release();
    };
    const NativeWorker = Worker;
    globalThis.Worker = class extends NativeWorker {
      override addEventListener(
        ...args: Parameters<Worker["addEventListener"]>
      ): void {
        const [type, listener, options] = args;
        (type === "message" && typeof listener === "function"
          ? () => {
              super.addEventListener(
                type,
                (event) => {
                  void released.then(() => {
                    listener.call(this, event);
                  });
                },
                options,
              );
            }
          : () => {
              super.addEventListener(...args);
            })();
      }
    };
  });
  await prepare(page);
  await expect(page.locator("#probe-benchmark")).toBeHidden();
  await page.goto("/offline-player/#probe");
  await page.reload();
  const benchmark = page.locator("#probe-benchmark");
  const overlay = page.locator("#probe");
  await selectAndPlay(page);
  await expect(overlay).toContainText(/audio sampleRate \d+ quantum [\d.]+ ms/);
  await expect(overlay).toContainText(/notifications [1-9]\d* interval p50 [\d.]+/);
  await expect(overlay).toContainText(/worklet load p50 [\d.]+%/);
  await expect(overlay).toContainText(/underrun \d+ events [\d.]+ s \/ [\d.]+ s/);
  /* The benchmark would load the Worklet it is compared with, so it cannot start while playing. */
  await expect(benchmark).toBeDisabled();
  await expect(overlay).toContainText("dsp bench -");
  const quantum = /audio sampleRate \d+ quantum ([\d.]+) ms/u.exec(
    (await overlay.textContent()) ?? "",
  )?.[1];
  await page.locator("#pause").click();
  await expect(page.locator("#play-status")).toHaveText("一時停止");
  await expect(benchmark).toBeEnabled();
  await benchmark.click();
  await expect(overlay).toContainText("dsp bench running");
  /* Playback cannot start while the benchmark runs either. */
  await expect(page.locator("#play")).toBeDisabled();
  await expect(benchmark).toBeDisabled();
  await page.evaluate(() => {
    (
      globalThis as typeof globalThis & { releaseBenchmark: () => void }
    ).releaseBenchmark();
  });
  await expect(overlay).toContainText(
    /dsp bench [1-9]\d* quanta of [\d.]+ ms \([\d.]+ s\) p50 [\d.]+ \/ p95 [\d.]+ \/ max [\d.]+ ms/,
  );
  /* After playing once, the benchmark decodes at the device rate, so its quantum matches the live one. */
  expect(quantum).toBeDefined();
  await expect(overlay).toContainText(`quanta of ${quantum ?? ""} ms`);
  await expect(page.locator("#play")).toBeEnabled();
  const notificationsBefore = Number(
    /notifications (\d+)/u.exec((await overlay.textContent()) ?? "")?.[1],
  );
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect
    .poll(async () =>
      Number(/notifications (\d+)/u.exec((await overlay.textContent()) ?? "")?.[1]),
    )
    .toBeGreaterThan(notificationsBefore);
  await expect(overlay).toContainText(/worklet load p50 [\d.]+%/);
  await expect(overlay).toContainText(/underrun \d+ events [\d.]+ s \/ [\d.]+ s/);
  /* Stopping drops the connection; the last playback stats stay on screen instead of disappearing or going back. */
  const played = async (): Promise<number> =>
    Number(
      /underrun \d+ events [\d.]+ s \/ ([\d.]+) s/u.exec(
        (await overlay.textContent()) ?? "",
      )?.[1] ?? Number.NaN,
    );
  const beforeStop = await played();
  await page.locator("#stop").click();
  await expect
    .poll(async () =>
      (await observe(page)).contexts.every((state) => state === "closed"),
    )
    .toBe(true);
  /* The overlay is refreshed once a second; wait for a refresh after the connection is gone. */
  await page.waitForTimeout(1100);
  expect(await played()).toBeGreaterThanOrEqual(beforeStop);
  expect((await observe(page)).errors).toEqual([]);
});

test("プレイヤーの状態から試聴画面の状態を進め、シークを挟んでも選択・再生・一時停止・停止の順に移る", async ({
  page,
}) => {
  const states = () =>
    page.evaluate(() => (globalThis as Scope).listeningProbe.states);
  const canvas = page.locator("#visualizer");
  await prepare(page);
  await selectAndPlay(page);
  await expect(canvas).toHaveAttribute("data-presentation-state", "playing");
  /* A seek while playing goes through seeking and comes back to playing. */
  await page.locator("#seek").fill("3");
  await expect(canvas).toHaveAttribute("data-presentation-state", "playing");
  await page.locator("#pause").click();
  await expect(canvas).toHaveAttribute("data-presentation-state", "paused");
  /* A seek while paused comes back to paused; the GPU stays released. */
  const devices = await page.evaluate(
    () => (globalThis as Scope).listeningProbe.devices.length,
  );
  await page.locator("#seek").fill("1");
  await expect(canvas).toHaveAttribute("data-presentation-state", "paused");
  expect(
    await page.evaluate(
      () => (globalThis as Scope).listeningProbe.devices.length,
    ),
  ).toBe(devices);
  await page.locator("#play").click();
  await expect(canvas).toHaveAttribute("data-presentation-state", "playing");
  await page.locator("#stop").click();
  await expect(canvas).toHaveAttribute("data-presentation-state", "selected");
  expect(await states()).toEqual([
    "idle",
    "selected",
    "playing",
    "seeking",
    "playing",
    "paused",
    "seeking",
    "paused",
    "playing",
    "selected",
  ]);
  expect((await observe(page)).errors).toEqual([]);
});

test("最後まで再生すると終了へ移り、選び直すと前の作品の状態を残さずに選択へ戻る", async ({
  page,
}) => {
  const canvas = page.locator("#visualizer");
  await prepare(page);
  await selectAndPlay(page, "Air study");
  await page.locator("#seek").fill("4.8");
  await expect(canvas).toHaveAttribute("data-presentation-state", "ended");
  await expect(page.locator("#play-status")).toHaveText("再生終了");
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await expect(canvas).toHaveAttribute("data-presentation-state", "selected");
  expect((await observe(page)).errors).toEqual([]);
});

test("音源の読み込みに失敗した後に停止すると、選択を残したまま選択の状態へ戻る", async ({
  page,
}) => {
  /* Audio blobs are replaced with bytes the media element cannot decode, so loading fails with the selection kept. */
  await page.addInitScript(() => {
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- The native static is invoked on URL itself. */
    const create = URL.createObjectURL;
    URL.createObjectURL = (object: Blob | MediaSource) =>
      create.call(
        URL,
        object instanceof Blob && object.type.startsWith("audio/")
          ? new Blob(["not audio"], { type: object.type })
          : object,
      );
  });
  const canvas = page.locator("#visualizer");
  await prepare(page);
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText(
    "読み込みに失敗しました",
  );
  await expect(canvas).toHaveAttribute("data-presentation-state", "error");
  await page.locator("#stop").click();
  await expect(page.locator("#play-status")).toHaveText("待機中");
  await expect(canvas).toHaveAttribute("data-presentation-state", "selected");
  await expect(
    page.getByRole("button", { name: "Reel study", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  expect((await observe(page)).errors).toEqual([]);
});

test("素早く続けて選び直しても、最後に選んだ作品だけを残し、前の作品の音や描画を残さない", async ({
  page,
}) => {
  const reel = page.getByRole("button", { name: "Reel study", exact: true });
  const air = page.getByRole("button", { name: "Air study", exact: true });
  const canvas = page.locator("#visualizer");
  await prepare(page);
  await selectAndPlay(page);
  /* Four selections in a row while playing, none of them awaited. */
  await air.click();
  await reel.click();
  await air.click();
  await reel.click();
  await air.click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await expect(page.locator("#track-title")).toHaveText("Air study");
  await expect(air).toHaveAttribute("aria-pressed", "true");
  await expect(reel).toHaveAttribute("aria-pressed", "false");
  await expect(canvas).toHaveAttribute("data-presentation-state", "selected");
  /* The first playback's audio graph is closed; none of the skipped selections left one behind. */
  await expect
    .poll(async () =>
      (await observe(page)).contexts.every((state) => state === "closed"),
    )
    .toBe(true);
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect(canvas).toHaveAttribute("data-presentation-state", "playing");
  await expect
    .poll(async () =>
      (await observe(page)).contexts.filter((state) => state !== "closed"),
    )
    .toEqual(["running"]);
  const featuresBefore = (await observe(page)).features.length;
  await expect
    .poll(async () => (await observe(page)).features.length)
    .toBeGreaterThan(featuresBefore);
  expect((await observe(page)).errors).toEqual([]);
});

test("音源の読み込みに失敗しても、別の作品を選び直せば再生と描画に戻る", async ({
  page,
}) => {
  /* Only the first audio blob is replaced with undecodable bytes; the next selection loads normally. */
  await page.addInitScript(() => {
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- The native static is invoked on URL itself. */
    const create = URL.createObjectURL;
    const remaining = { failures: 1 };
    URL.createObjectURL = (object: Blob | MediaSource) => {
      const broken =
        object instanceof Blob &&
        object.type.startsWith("audio/") &&
        remaining.failures > 0;
      remaining.failures -= broken ? 1 : 0;
      return create.call(
        URL,
        broken ? new Blob(["not audio"], { type: object.type }) : object,
      );
    };
  });
  const canvas = page.locator("#visualizer");
  await prepare(page);
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText(
    "読み込みに失敗しました",
  );
  await expect(canvas).toHaveAttribute("data-presentation-state", "error");
  await selectAndPlay(page, "Air study");
  await expect(canvas).toHaveAttribute("data-presentation-state", "playing");
  await expect(page.locator("#error")).toBeEmpty();
  await expect
    .poll(async () => (await observe(page)).features.length)
    .toBeGreaterThan(2);
  await expect
    .poll(async () => (await observe(page)).uniforms.length)
    .toBeGreaterThan(0);
  expect((await observe(page)).errors).toEqual([]);
});

/* Tablet-like sizes in both orientations; the narrow phone portrait is covered by the offline listening test. */
(
  [
    ["landscape", { width: 1280, height: 800 }],
    ["portrait", { width: 800, height: 1280 }],
  ] as const
).forEach(([orientation, size]) => {
  test(`${orientation}: 操作でき、横にはみ出さず、日本語を同梱の書体で描く`, async ({
    page,
  }) => {
    await page.setViewportSize(size);
    await prepare(page);
    await selectAndPlay(page);
    await page.locator("#pause").click();
    await expect(page.locator("#play-status")).toHaveText("一時停止");
    await page.locator("#seek").fill("2");
    await expect(page.locator("#position")).toHaveText("0:02");
    await page.locator("#play").click();
    await expect(page.locator("#play-status")).toHaveText("再生中");
    /* The evidence shows the drawing, not the moment before the first frame. */
    await expect(page.locator("#visualizer-status")).toHaveText(
      "音に合わせて描画します",
    );
    const drawn = (await observe(page)).uniforms.length;
    await expect
      .poll(async () => (await observe(page)).uniforms.length)
      .toBeGreaterThan(drawn);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    const typefaces = await loadTypefaces(page);
    expect(
      typefaces.filter((face) => face.status === "loaded").length,
    ).toBeGreaterThan(0);
    expect(await drawnWithTypeface(page, JAPANESE_SAMPLE)).toBe(true);
    await page.screenshot({
      path: `test-results/listening-tablet-${orientation}.png`,
      fullPage: true,
    });
    await page.locator("#stop").click();
    await expect(page.locator("#visualizer")).toHaveAttribute(
      "data-presentation-state",
      "selected",
    );
    expect((await observe(page)).errors).toEqual([]);
  });
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
