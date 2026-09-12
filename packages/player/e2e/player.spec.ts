/* eslint-disable functional/immutable-data -- Test-only instrumentation records real media resources and injects failures without replacing successful FLAC playback. */
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

type Probe = {
  media: HTMLAudioElement[];
  created: string[];
  revoked: string[];
  errors: string[];
  ignoreAbort: boolean;
  nextPlay: "normal" | "reject" | "defer";
  pending: (() => void) | null;
};
type Scope = typeof globalThis & { playerProbe: Probe };

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const probe: Probe = {
      media: [],
      created: [],
      revoked: [],
      errors: [],
      ignoreAbort: false,
      nextPlay: "normal",
      pending: null,
    };
    (globalThis as Scope).playerProbe = probe;
    const NativeAudio = globalThis.Audio;
    globalThis.Audio = class extends NativeAudio {
      constructor(...args: ConstructorParameters<typeof NativeAudio>) {
        super(...args);
        probe.media.push(this);
      }
    };
    const create = URL.createObjectURL.bind(URL);
    const revoke = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = (blob) => {
      const url = create(blob);
      probe.created.push(url);
      return url;
    };
    URL.revokeObjectURL = (url) => {
      probe.revoked.push(url);
      revoke(url);
    };
    const fetch = globalThis.fetch.bind(globalThis);
    globalThis.fetch = (input, options) =>
      fetch(input, probe.ignoreAbort ? { ...options, signal: null } : options);
    /* eslint-disable-next-line @typescript-eslint/unbound-method -- The original native play method is called with its actual media instance. */
    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      const mode = probe.nextPlay;
      probe.nextPlay = "normal";
      return mode === "reject"
        ? Promise.reject(
            new DOMException("test playback denied", "NotAllowedError"),
          )
        : mode === "defer"
          ? play.call(this).then(
              () =>
                new Promise<void>((_resolve, reject) => {
                  probe.pending = () => {
                    reject(
                      new DOMException("late play rejection", "AbortError"),
                    );
                  };
                }),
            )
          : play.call(this);
    };
    globalThis.addEventListener("error", (event) => {
      probe.errors.push(event.message);
    });
    globalThis.addEventListener("unhandledrejection", (event) => {
      probe.errors.push(String(event.reason));
    });
  });
});
const probe = (page: Page) =>
  page.evaluate(() => {
    const state = (globalThis as Scope).playerProbe;
    return {
      created: state.created,
      revoked: state.revoked,
      errors: state.errors,
      media: state.media.map((audio) => ({
        paused: audio.paused,
        source: audio.currentSrc,
        src: audio.getAttribute("src"),
        networkState: audio.networkState,
        readyState: audio.readyState,
        time: audio.currentTime,
      })),
    };
  });
const select = async (page: Page, title: string): Promise<void> => {
  await page.getByRole("button", { name: title, exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
};
const play = async (page: Page): Promise<void> => {
  await page.getByRole("button", { name: "再生", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect
    .poll(async () =>
      (await probe(page)).media.some((audio) => audio.time > 0.3),
    )
    .toBe(true);
};
const released = async (page: Page): Promise<void> => {
  await expect
    .poll(async () =>
      (await probe(page)).media.every(
        (audio) =>
          audio.paused &&
          audio.src === null &&
          audio.networkState === 0 &&
          audio.readyState === 0,
      ),
    )
    .toBe(true);
  const state = await probe(page);
  expect([...state.created].sort()).toEqual([...state.revoked].sort());
  expect(new Set(state.revoked).size).toBe(state.revoked.length);
  expect(state.errors).toEqual([]);
};

test("Manifestの曲目からFLACを再生し、一時停止・シーク・再開・停止できる", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("navigation", { name: "アルバムと曲目" }),
  ).toContainText("Evening Session");
  await expect(
    page.getByRole("button", { name: "再生", exact: true }),
  ).toBeDisabled();
  await select(page, "Reel study");
  await expect(page.locator("#duration")).toHaveText("0:08");
  await play(page);
  await page.screenshot({
    path: "test-results/player-playing.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "一時停止", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("一時停止");
  const paused = (await probe(page)).media[0]?.time;
  await page.waitForTimeout(350);
  expect((await probe(page)).media[0]?.time).toBe(paused);
  await page.locator("#seek").fill("4");
  await expect(page.locator("#position")).toHaveText("0:04");
  await play(page);
  await page.getByRole("button", { name: "停止", exact: true }).click();
  await expect(page.locator("#position")).toHaveText("0:00");
  await released(page);
  await page
    .getByRole("button", { name: "音源を読み直す", exact: true })
    .click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await play(page);
});

test("再生中の曲切替で旧音源を解放し、別アルバムも選曲できる", async ({
  page,
}) => {
  await page.goto("/");
  await select(page, "Reel study");
  await play(page);
  await select(page, "Air study");
  await expect(page.locator("#duration")).toHaveText("0:05");
  await expect
    .poll(async () => (await probe(page)).media[0]?.networkState)
    .toBe(0);
  await expect(page.locator("#position")).toHaveText("0:00");
  await play(page);
  await select(page, "Return");
  await expect(page.locator("#album-title")).toHaveText("Evening Session");
  await page.getByRole("button", { name: "停止", exact: true }).click();
  await released(page);
});

test("遅い旧読み込みが中止を無視して完了しても新曲を上書きしない", async ({
  page,
}) => {
  const gate: { open: (() => void) | null; entered: boolean } = {
    open: null,
    entered: false,
  };
  await page.route("**/fixtures/first.flac", async (route) => {
    gate.entered = true;
    await new Promise<void>((resolve) => {
      gate.open = resolve;
    });
    await route.continue();
  });
  await page.goto("/");
  await page.evaluate(() => {
    (globalThis as Scope).playerProbe.ignoreAbort = true;
  });
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect.poll(() => gate.entered).toBe(true);
  await select(page, "Air study");
  const response = page.waitForResponse("**/fixtures/first.flac");
  gate.open?.();
  await response;
  await page.waitForTimeout(300);
  await expect(page.locator("#track-title")).toHaveText("Air study");
  expect((await probe(page)).created).toHaveLength(1);
  await play(page);
});

test("読み込み中の停止で、遅延完了からURLや音声が復活しない", async ({
  page,
}) => {
  const gate: { open: (() => void) | null; entered: boolean } = {
    open: null,
    entered: false,
  };
  await page.route("**/fixtures/first.flac", async (route) => {
    gate.entered = true;
    await new Promise<void>((resolve) => {
      gate.open = resolve;
    });
    await route.continue();
  });
  await page.goto("/");
  await page.evaluate(() => {
    (globalThis as Scope).playerProbe.ignoreAbort = true;
  });
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect.poll(() => gate.entered).toBe(true);
  await page.getByRole("button", { name: "停止", exact: true }).click();
  const response = page.waitForResponse("**/fixtures/first.flac");
  gate.open?.();
  await response;
  await page.waitForTimeout(300);
  await expect(page.locator("#play-status")).toHaveText("待機中");
  expect((await probe(page)).created).toHaveLength(0);
  await released(page);
});

[
  { name: "取得失敗", status: 503, body: "unavailable" },
  { name: "破損FLAC", status: 200, body: "not a FLAC file" },
].forEach((failure) => {
  test(`${failure.name}を表示し、再取得で回復できる`, async ({ page }) => {
    await page.route("**/fixtures/first.flac", (route) =>
      route.fulfill({
        status: failure.status,
        body: failure.body,
        contentType: "audio/flac",
      }),
    );
    await page.goto("/");
    await page.getByRole("button", { name: "Reel study", exact: true }).click();
    await expect(page.locator("#play-status")).toHaveText(
      "読み込みに失敗しました",
    );
    await expect(page.locator("#error")).not.toBeEmpty();
    await released(page);
    await page.unroute("**/fixtures/first.flac");
    await page
      .getByRole("button", { name: "音源を読み直す", exact: true })
      .click();
    await expect(page.locator("#play-status")).toHaveText("再生できます");
    await expect(page.locator("#error")).toBeEmpty();
    await play(page);
  });
});

test("再生拒否を成功扱いせず、ユーザー操作で再試行できる", async ({ page }) => {
  await page.goto("/");
  await select(page, "Reel study");
  await page.evaluate(() => {
    (globalThis as Scope).playerProbe.nextPlay = "reject";
  });
  await page.getByRole("button", { name: "再生", exact: true }).click();
  await expect(page.locator("#error")).toContainText("再生ボタンで再試行");
  await expect(page.locator("#play-status")).toHaveText("一時停止");
  await play(page);
  await expect(page.locator("#error")).toBeEmpty();
});

test("停止後の古いplay拒否やmediaイベントを無視する", async ({ page }) => {
  await page.goto("/");
  await select(page, "Reel study");
  await page.evaluate(() => {
    (globalThis as Scope).playerProbe.nextPlay = "defer";
  });
  await page.getByRole("button", { name: "再生", exact: true }).click();
  await expect
    .poll(() =>
      page.evaluate(() => (globalThis as Scope).playerProbe.pending === null),
    )
    .toBe(false);
  await page.getByRole("button", { name: "停止", exact: true }).click();
  await page.evaluate(() => {
    const state = (globalThis as Scope).playerProbe;
    state.pending?.();
    state.media[0]?.dispatchEvent(new Event("error"));
    state.media[0]?.dispatchEvent(new Event("ended"));
    state.media[0]?.dispatchEvent(new Event("loadedmetadata"));
  });
  await expect(page.locator("#play-status")).toHaveText("待機中");
  await expect(page.locator("#error")).toBeEmpty();
  await released(page);
});

test("終端を表示し、再生し直せる", async ({ page }) => {
  await page.goto("/");
  await select(page, "Air study");
  await play(page);
  await page.locator("#seek").fill("4.8");
  await expect(page.locator("#play-status")).toHaveText("再生終了");
  await play(page);
  await expect
    .poll(async () => (await probe(page)).media[0]?.time)
    .toBeLessThan(3);
});

test("縦画面でも操作でき、ページ破棄で全資源を解放する", async ({ page }) => {
  await page.setViewportSize({ width: 420, height: 900 });
  await page.goto("/");
  await select(page, "Return");
  await play(page);
  await expect(
    page.getByRole("button", { name: "一時停止", exact: true }),
  ).toBeInViewport();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/player-portrait.png",
    fullPage: true,
  });
  await page.evaluate(() => {
    dispatchEvent(new Event("pagehide"));
  });
  await released(page);
  await page.getByRole("button", { name: "Reel study", exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("待機中");
  expect((await probe(page)).created).toHaveLength(1);
});
