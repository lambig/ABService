/* eslint-disable functional/immutable-data -- Browser probes observe actual media playback and delay only preparation in cancellation tests. */
import { expect, test } from "@playwright/test";
import { distribute, prepareAndReload, openManagement } from "./support";

type Probe = {
  media: HTMLAudioElement[];
  decode: number;
  delay: boolean;
  release?: () => void;
  errors: string[];
};
type Scope = typeof globalThis & { exhibitionProbe: Probe };
test.beforeEach(async ({ page, request }) => {
  await distribute(request, "v1");
  await page.addInitScript(() => {
    const probe: Probe = { media: [], decode: 0, delay: false, errors: [] };
    (globalThis as Scope).exhibitionProbe = probe;
    const Audio = globalThis.Audio;
    globalThis.Audio = class extends Audio {
      constructor(...args: ConstructorParameters<typeof Audio>) {
        super(...args);
        probe.media.push(this);
      }
    };
    const Offline = globalThis.OfflineAudioContext;
    globalThis.OfflineAudioContext = new Proxy(Offline, {
      construct(target, args: ConstructorParameters<typeof Offline>) {
        probe.decode += 1;
        return Reflect.construct(target, args);
      },
    });
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- Preserve the native worklet receiver. */
    const add = AudioWorklet.prototype.addModule;
    AudioWorklet.prototype.addModule = async function (...args) {
      await (probe.delay
        ? new Promise<void>((resolve) => {
            probe.release = resolve;
          })
        : Promise.resolve());
      await add.apply(this, args);
    };
    addEventListener("unhandledrejection", (event) => {
      probe.errors.push(String(event.reason));
    });
  });
  await prepareAndReload(page);
});

test("画像選択から展示開始し、終端で同じ作品を繰り返し、別作品へ連続切替する", async ({
  page,
  context,
}) => {
  await context.setOffline(true);
  const cover = page.getByRole("button", {
    name: "Album crossfade",
    exact: true,
  });
  await expect(cover.locator("img")).toBeVisible();
  await cover.click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await expect(page.locator("#waveform-status")).toBeEmpty();
  await expect(page.locator(".waveform-path").first()).not.toHaveAttribute(
    "d",
    "",
  );
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect(page.locator("#play")).toBeHidden();
  await expect(page.locator("#pause")).toBeHidden();
  await expect(page.locator("#stop")).toBeHidden();
  await page.locator("#seek").fill("7.7");
  await expect
    .poll(() => page.locator("#seek").inputValue().then(Number))
    .toBeLessThan(2);
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect(cover).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Air study", exact: true }).click();
  await expect(page.locator("#track-title")).toHaveText("Air study");
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect
    .poll(() => page.locator("#seek").inputValue().then(Number))
    .toBeGreaterThan(0.2);
  await expect(page.locator("#error")).toBeEmpty();
});

test("展示終了後は同じ作品を開始だけで再生し、波形をオフライン再起動でも再利用する", async ({
  page,
  context,
}) => {
  await page
    .getByRole("button", { name: "Album crossfade", exact: true })
    .click();
  await expect(page.locator("#waveform-status")).toBeEmpty();
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await openManagement(page);
  await page.locator("#exhibition-menu > summary").click();
  await page.locator("#stop").click();
  await expect(page.locator("#position")).toHaveText("0:00");
  await expect(page.locator("#retry")).toBeHidden();
  await expect
    .poll(() =>
      page.evaluate(() =>
        (globalThis as Scope).exhibitionProbe.media.every(
          (audio) => audio.paused && audio.getAttribute("src") === null,
        ),
      ),
    )
    .toBe(true);
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await context.setOffline(true);
  await page.reload();
  await page
    .getByRole("button", { name: "Album crossfade", exact: true })
    .click();
  await expect(page.locator("#waveform-status")).toBeEmpty();
  expect(
    await page.evaluate(() => (globalThis as Scope).exhibitionProbe.decode),
  ).toBe(0);
  await expect(page.locator(".waveform-path").first()).not.toHaveAttribute(
    "d",
    "",
  );
});

for (const next of ["stop", "select"] as const) {
  test(`作品準備中の${next}で古い音源が後から鳴らない`, async ({ page }) => {
    await page
      .getByRole("button", { name: "Album crossfade", exact: true })
      .click();
    await expect(page.locator("#play-status")).toHaveText("再生できます");
    await page.locator("#play").click();
    await expect(page.locator("#play-status")).toHaveText("再生中");
    await page.evaluate(() => {
      (globalThis as Scope).exhibitionProbe.delay = true;
    });
    await page.getByRole("button", { name: "Air study", exact: true }).click();
    await expect(page.locator("#play-status")).toHaveText("読み込み中");
    await expect
      .poll(() =>
        page.evaluate(
          () => typeof (globalThis as Scope).exhibitionProbe.release,
        ),
      )
      .toBe("function");
    await (next === "stop"
      ? (async () => {
          await openManagement(page);
          await page.locator("#exhibition-menu > summary").click();
          await page.locator("#stop").click();
        })()
      : (async () => {
          await page.evaluate(() => {
            (globalThis as Scope).exhibitionProbe.delay = false;
          });
          await page
            .getByRole("button", { name: "Return", exact: true })
            .click();
          await expect(page.locator("#play-status")).toHaveText("再生中");
        })());
    await page.evaluate(() => {
      (globalThis as Scope).exhibitionProbe.release?.();
    });
    await page.waitForTimeout(200);
    expect(
      await page.evaluate(() =>
        (globalThis as Scope).exhibitionProbe.media
          .slice(0, 2)
          .every((audio) => audio.paused && audio.getAttribute("src") === null),
      ),
    ).toBe(true);
    await expect(page.locator("#play-status")).toHaveText(
      next === "stop" ? "停止中" : "再生中",
    );
    expect(
      await page.evaluate(() => (globalThis as Scope).exhibitionProbe.errors),
    ).toEqual([]);
  });
}

test("縦画面で画像の横送りが見え、波形をキーボード操作でき、全画面の解除に追従する", async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".gallery-hint")).toContainText("左右にスワイプ");
  await page
    .getByRole("button", { name: "Album crossfade", exact: true })
    .click();
  await expect(page.locator("#waveform-status")).toBeEmpty();
  await page.locator("#seek").focus();
  await page.keyboard.press("ArrowRight");
  await expect
    .poll(() => page.locator("#seek").inputValue().then(Number))
    .toBeGreaterThan(0);
  await page.screenshot({ path: testInfo.outputPath("portrait.png") });
  await openManagement(page);
  await page.locator("#fullscreen").click();
  await expect
    .poll(() => page.evaluate(() => document.fullscreenElement !== null))
    .toBe(true);
  await expect(page.locator("#fullscreen")).toHaveText("全画面を解除");
  await page.evaluate(() => document.exitFullscreen());
  await expect(page.locator("#fullscreen")).toHaveText("全画面");
  await expect(page.locator("#seek")).toBeInViewport();
});
