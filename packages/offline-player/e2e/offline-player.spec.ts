/* eslint-disable functional/immutable-data -- Test-only request observations record attempted audio downloads. */
import { expect, test } from "@playwright/test";
import type { BrowserContext, Page } from "@playwright/test";
import { manifest } from "player-study/fixture";
import {
  JAPANESE_SAMPLE,
  LATIN_SAMPLE,
  drawnWithTypeface,
  loadTypefaces,
} from "player-study-e2e/typeface";

const entry = "/offline-player/";
const saved = "オフライン再生の準備ができました";
const prepare = async (page: Page): Promise<void> => {
  await expect(page.locator("#prepare")).toBeEnabled();
  await page.locator("#prepare").click();
  await expect(page.locator("#readiness")).toHaveText(saved);
};
const restart = async (page: Page, context: BrowserContext): Promise<Page> => {
  await page.close();
  await context.setOffline(true);
  const next = await context.newPage();
  const response = await next.goto(entry);
  expect(response?.fromServiceWorker()).toBe(true);
  return next;
};
const play = async (page: Page, title = "Reel study"): Promise<void> => {
  await page.getByRole("button", { name: title, exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect
    .poll(() => page.locator("#seek").inputValue().then(Number))
    .toBeGreaterThan(0.3);
};
const damage = async (
  page: Page,
  fault: "missing" | "corrupt",
): Promise<void> => {
  const asset = manifest.assets[1];
  expect(asset).toBeDefined();
  await page.evaluate(
    async ({ asset, fault }) => {
      const value = asset as NonNullable<typeof asset>;
      const bytes = new TextEncoder().encode(
        JSON.stringify([
          value.assetId,
          value.byteLength,
          value.checksum.algorithm,
          value.checksum.value,
        ]),
      );
      const name = Array.from(
        new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
        (byte) => byte.toString(16).padStart(2, "0"),
      ).join("");
      const directory = await (
        await navigator.storage.getDirectory()
      ).getDirectoryHandle("abservice-assets-v1");
      const corrupt = async (): Promise<void> => {
        const file = await directory.getFileHandle(name);
        const stream = await file.createWritable();
        await stream.write(new Uint8Array(value.byteLength));
        await stream.close();
      };
      await (
        fault === "missing" ? () => directory.removeEntry(name) : corrupt
      )();
    },
    { asset, fault },
  );
};

test("保存後の新ページを通信なしで起動し、選曲・再生・停止・再取得できる", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await expect(page.locator("#readiness")).toContainText("未完了");
  await prepare(page);
  const next = await restart(page, context);
  await expect(next.locator("#readiness")).toHaveText(saved);
  /* 書体は shell の収録対象。通信なしでも面が届き、日本語の見本が同梱書体の字形で描かれる */
  const typefaces = await loadTypefaces(next);
  expect(typefaces.filter((face) => face.status === "loaded")).toHaveLength(4);
  expect(await drawnWithTypeface(next, JAPANESE_SAMPLE)).toBe(true);
  expect(await drawnWithTypeface(next, LATIN_SAMPLE)).toBe(true);
  const downloads: string[] = [];
  next.on("request", (request) => {
    [request.url()]
      .filter((url) => url.includes("/audio/"))
      .forEach((url) => {
        downloads.push(url);
      });
  });
  await expect(
    next.getByRole("button", { name: "Metadata-only song", exact: true }),
  ).toHaveCount(0);
  await play(next, "Album crossfade");
  await next.screenshot({
    path: "test-results/offline-player-playing.png",
    fullPage: true,
  });
  await next.locator("#pause").click();
  await expect(next.locator("#play-status")).toHaveText("一時停止");
  await next.locator("#stop").click();
  await next.locator("#retry").click();
  await expect(next.locator("#play-status")).toHaveText("再生できます");
  await next.setViewportSize({ width: 420, height: 860 });
  await play(next, "Air study");
  await next.screenshot({
    path: "test-results/offline-player-portrait.png",
    fullPage: true,
  });
  expect(
    await next.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect(downloads).toEqual([]);
});

(["missing", "corrupt"] as const).forEach((fault) => {
  test(`${fault}: 起動時に検出し再生を拒否、オンライン再保存から回復する`, async ({
    page,
    context,
  }) => {
    await page.goto(entry);
    await prepare(page);
    await damage(page, fault);
    const next = await restart(page, context);
    await expect(next.locator("#readiness")).toContainText("未完了");
    await play(next);
    await next.getByRole("button", { name: "Air study", exact: true }).click();
    await expect(next.locator("#play-status")).toHaveText(
      "読み込みに失敗しました",
    );
    await expect(next.locator("#error")).toContainText(
      fault === "missing" ? "未保存" : "破損",
    );
    await expect(next.locator("#play")).toBeDisabled();
    await context.setOffline(false);
    await prepare(next);
    const repaired = await restart(next, context);
    await expect(repaired.locator("#readiness")).toHaveText(saved);
    await play(repaired, "Air study");
  });
});

test("一部の音源取得に失敗しても準備完了とせず、再保存で回復する", async ({
  page,
  context,
}) => {
  await context.route("**/audio/second.flac", (route) =>
    route.fulfill({ status: 503, body: "unavailable" }),
  );
  await page.goto(entry);
  await expect(page.locator("#prepare")).toBeEnabled();
  await page.locator("#prepare").click();
  await expect(page.locator("#preparation-detail")).toContainText(
    "音源を取得できませんでした",
  );
  const next = await restart(page, context);
  await expect(next.locator("#readiness")).toContainText("未完了");
  await play(next);
  await context.setOffline(false);
  await context.unroute("**/audio/second.flac");
  await prepare(next);
  const repaired = await restart(next, context);
  await expect(repaired.locator("#readiness")).toHaveText(saved);
  await play(repaired, "Air study");
});

test("音源が揃っていてもshell欠落を準備完了としない", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await prepare(page);
  await page.evaluate(async () => {
    const names = await caches.keys();
    await Promise.all(
      names
        .filter((name) => name.includes("/offline-player/"))
        .map(async (name) => {
          const cache = await caches.open(name);
          const requests = await cache.keys();
          await Promise.all(
            requests
              .filter((request) => request.url.endsWith("/index.html"))
              .map((request) => cache.delete(request)),
          );
        }),
    );
  });
  await page.locator("#inspect").click();
  await expect(page.locator("#readiness")).toContainText("未完了");
  await page.close();
  await context.setOffline(true);
  const next = await context.newPage();
  expect((await next.goto(entry))?.status()).toBe(503);
  await expect(next.locator("body")).toContainText("欠落・破損");
});

test("書体の欠落を準備完了とせず、通信なしの起動で字形を偽らない", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await prepare(page);
  await page.evaluate(async () => {
    const names = await caches.keys();
    await Promise.all(
      names
        .filter((name) => name.includes("/offline-player/"))
        .map(async (name) => {
          const cache = await caches.open(name);
          const requests = await cache.keys();
          await Promise.all(
            requests
              .filter((request) => request.url.endsWith(".woff2"))
              .map((request) => cache.delete(request)),
          );
        }),
    );
  });
  await page.locator("#inspect").click();
  await expect(page.locator("#readiness")).toContainText("未完了");
  await page.close();
  await context.setOffline(true);
  const next = await context.newPage();
  /* 画面そのものは残っているため開けるが、面は届かず、OS の書体で描かれる */
  expect((await next.goto(entry))?.status()).toBe(200);
  await expect(next.locator("#readiness")).toContainText("未完了");
  const typefaces = await loadTypefaces(next);
  expect(typefaces.filter((face) => face.status === "loaded")).toHaveLength(0);
  expect(await drawnWithTypeface(next, JAPANESE_SAMPLE)).toBe(false);
});
