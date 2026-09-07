/* eslint-disable functional/immutable-data -- Browser instrumentation records real contexts without replacing their Web Audio behavior. */
import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const scope = globalThis as typeof globalThis & {
      audioContexts: AudioContext[];
      loadMode: "normal" | "hold" | "fail";
      releaseModule: () => void;
    };
    scope.audioContexts = [];
    scope.loadMode = "normal";
    scope.AudioContext = class extends AudioContext {
      constructor(options?: AudioContextOptions) {
        super(options);
        scope.audioContexts.push(this);
        const addModule = this.audioWorklet.addModule.bind(this.audioWorklet);
        this.audioWorklet.addModule = (url, options) =>
          scope.loadMode === "fail"
            ? Promise.reject(new Error("Injected module load failure"))
            : scope.loadMode === "hold"
              ? new Promise<void>((resolve, reject) => {
                  scope.releaseModule = () => {
                    void addModule(url, options).then(resolve, reject);
                  };
                })
              : addModule(url, options);
      }
    };
  });
});

const contextsClosed = async (
  page: import("@playwright/test").Page,
): Promise<void> => {
  await expect
    .poll(() =>
      page.evaluate(() => {
        const scope = globalThis as typeof globalThis & {
          audioContexts: AudioContext[];
        };
        return (
          scope.audioContexts.length > 0 &&
          scope.audioContexts.every((context) => context.state === "closed")
        );
      }),
    )
    .toBe(true);
};

test("実AudioWorkletでmono/stereoのRMSを通知し、停止後に再開始できる", async ({
  page,
}) => {
  await page.goto("/");
  const verify = async (fixture: string, rms: number): Promise<void> => {
    await page.locator("#fixture").selectOption(fixture);
    await page.locator("#start").click();
    await expect(page.getByRole("status")).toHaveText("再生中");
    await expect
      .poll(async () => Number(await page.locator("#count").textContent()))
      .toBeGreaterThan(10);
    await expect
      .poll(async () => Number(await page.locator("#rms").textContent()))
      .toBeCloseTo(rms, 2);
    await page.locator("#stop").click();
    await contextsClosed(page);
    const stopped = await page.locator("#count").textContent();
    await page.waitForTimeout(250);
    await expect(page.locator("#count")).toHaveText(stopped ?? "0");
  };
  await verify("mono", Math.SQRT1_2 / 10);
  await verify("stereo", Math.SQRT1_2 / 10);
  await verify("opposed", Math.SQRT1_2 / 10);
  await verify("left-only", 0.05);
  await verify("silence", 0);
  await page.screenshot({ path: "test-results/rms-session.png" });
});

test("通知頻度がaudio block周期から独立する", async ({ page }) => {
  await page.goto("/");
  const verify = async (rate: string): Promise<void> => {
    await page.locator("#rate").selectOption(rate);
    await page.locator("#start").click();
    await expect
      .poll(async () => Number(await page.locator("#count").textContent()))
      .toBeGreaterThan(15);
    const frequency = Number(await page.locator("#frequency").textContent());
    expect(frequency).toBeGreaterThan(Number(rate) * 0.9);
    expect(frequency).toBeLessThan(Number(rate) * 1.1);
    await page.locator("#stop").click();
    await contextsClosed(page);
  };
  await verify("10");
  await verify("60");
});

test("初期化中の停止で通知が漏れず、再開始できる", async ({ page }) => {
  await page.goto("/");
  await page.evaluate(() => {
    (globalThis as typeof globalThis & { loadMode: string }).loadMode = "hold";
  });
  await page.locator("#start").click();
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          typeof (
            globalThis as typeof globalThis & { releaseModule?: () => void }
          ).releaseModule,
      ),
    )
    .toBe("function");
  await expect(page.getByRole("status")).toHaveText("準備中");
  await page.locator("#stop").click();
  await contextsClosed(page);
  await page.evaluate(() => {
    const scope = globalThis as typeof globalThis & {
      loadMode: string;
      releaseModule: () => void;
    };
    scope.loadMode = "normal";
    scope.releaseModule();
  });
  await page.waitForTimeout(250);
  await expect(page.getByRole("status")).toHaveText("停止中");
  await expect(page.locator("#count")).toHaveText("0");
  await page.locator("#start").click();
  await expect
    .poll(async () => Number(await page.locator("#count").textContent()))
    .toBeGreaterThan(5);
  await page.locator("#stop").click();
  await contextsClosed(page);
});

test("Workletのロード失敗でcontextを閉じ、次の開始で回復する", async ({
  page,
}) => {
  await page.goto("/");
  await page.evaluate(() => {
    (globalThis as typeof globalThis & { loadMode: string }).loadMode = "fail";
  });
  await page.locator("#start").click();
  await contextsClosed(page);
  await expect(page.locator("#count")).toHaveText("0");
  await expect(page.getByRole("status")).not.toHaveText("準備中");
  await page.evaluate(() => {
    (globalThis as typeof globalThis & { loadMode: string }).loadMode =
      "normal";
  });
  await page.locator("#start").click();
  await expect
    .poll(async () => Number(await page.locator("#count").textContent()))
    .toBeGreaterThan(5);
  await page.screenshot({ path: "test-results/rms-playing.png" });
  await page.locator("#stop").click();
  await contextsClosed(page);
});
