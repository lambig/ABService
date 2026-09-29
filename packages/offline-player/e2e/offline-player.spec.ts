/* eslint-disable functional/immutable-data -- Test-only request and console observations record what the page fetched and logged. */
import { expect, test } from "@playwright/test";
import type { BrowserContext, Page } from "@playwright/test";
import { manifest } from "player-study/fixture";
import {
  JAPANESE_SAMPLE,
  LATIN_SAMPLE,
  drawnWithTypeface,
  loadTypefaces,
} from "player-study-e2e/typeface";
import {
  artworkAssetId,
  distributions,
  fixtureSignature,
  fixtureToken,
} from "../distribution";
import {
  distribute,
  entry,
  openPreparation,
  prepareOnline,
  restart,
  saved,
  storedAssets,
  unready,
} from "./support";

const play = async (page: Page, title = "Reel study"): Promise<void> => {
  await page.getByRole("button", { name: title, exact: true }).click();
  await expect(page.locator("#play-status")).toHaveText("再生できます");
  await page.locator("#play").click();
  await expect(page.locator("#play-status")).toHaveText("再生中");
  await expect
    .poll(() => page.locator("#seek").inputValue().then(Number))
    .toBeGreaterThan(0.3);
};
/* Records every audio download, so that tests can tell what a retry fetched again. */
const downloads = (context: BrowserContext): string[] => {
  const seen: string[] = [];
  /* Observed on the context: requests of a page under the Service Worker may not reach page events. */
  context.on("request", (request) => {
    [new URL(request.url()).pathname]
      .filter((path) => path.includes("/audio/"))
      .forEach((path) => {
        seen.push(path);
      });
  });
  return seen;
};
/* Everything a person or a log could see must be free of the token and the signed URL. */
const logs = (page: Page): string[] => {
  const seen: string[] = [];
  page.on("console", (message) => {
    seen.push(message.text());
  });
  return seen;
};
const expectNoSecrets = async (
  page: Page,
  logged: readonly string[],
): Promise<void> => {
  const visible = await page.locator("body").innerText();
  [visible, ...logged].forEach((text) => {
    expect(text).not.toContain(fixtureToken);
    expect(text).not.toContain(fixtureSignature);
  });
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
const secondAudio = /\/offline-player\/audio\/second\.flac/;
const secondUrl = /\/api\/v1\/listening\/package\/assets\/tone-second\/url$/;

test.beforeEach(async ({ request }) => {
  await distribute(request, "v1");
});

test("配布元から準備し、全タブを閉じた後の通信なしの新ページで選曲・再生・停止・再取得できる", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await expect(page.locator("#readiness")).toHaveText(unready);
  await prepareOnline(page);
  /* 音源 2 つと artwork の 3 つを、この端末の保存領域へ内容で識別して置く */
  expect(await storedAssets(page)).toBe(3);
  const next = await restart(page, context);
  await expect(next.locator("#readiness")).toHaveText(saved);
  await expect(next.locator("#token")).toHaveValue("");
  /* 書体は shell の収録対象。通信なしでも面が届き、日本語の見本が同梱書体の字形で描かれる */
  const typefaces = await loadTypefaces(next);
  expect(typefaces.filter((face) => face.status === "loaded")).toHaveLength(4);
  expect(await drawnWithTypeface(next, JAPANESE_SAMPLE)).toBe(true);
  expect(await drawnWithTypeface(next, LATIN_SAMPLE)).toBe(true);
  const fetched = downloads(context);
  await expect(
    next.getByRole("button", { name: "Metadata-only song", exact: true }),
  ).toHaveCount(0);
  const asked: string[] = [];
  context.on("request", (request) => {
    asked.push(request.url());
  });
  /* 準備済みの端末では、通常の試聴に token の入力を出さない */
  await expect(next.locator("#preparation")).toBeHidden();
  await expect(next.locator("#open-preparation")).toBeVisible();
  await play(next, "Album crossfade");
  /* 作品情報は保存済みの package から出す。artwork は検証済みの実体を描き、説明の画像とリンクは取りに行かない */
  await expect(next.locator("#album-title")).toHaveText("Northbound · Study 01");
  await expect(next.locator("#artist")).toHaveText("AB Study");
  await expect(next.locator("#description")).toContainText("検証用");
  await expect(next.locator("#description")).toContainText("頒布ページ を参照。");
  await expect(next.locator("#description a, #description img")).toHaveCount(0);
  await expect
    .poll(() =>
      next
        .locator("#artwork")
        .evaluate((image: HTMLImageElement) => image.naturalWidth),
    )
    .toBe(1);
  expect(
    asked.filter((url) =>
      ["example.com", "inline.png", artworkAssetId].some((part) =>
        url.includes(part),
      ),
    ),
  ).toEqual([]);
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
  expect(fetched).toEqual([]);
});

test("別タブが旧版を使っている間は切り替えず、全タブを閉じた後に新版へ昇格し、旧版だけの実体を削除する", async ({
  page,
  context,
  request,
}) => {
  await page.goto(entry);
  await prepareOnline(page);
  const first = await restart(page, context);
  await expect(first.locator("#readiness")).toHaveText(saved);
  await context.setOffline(false);
  const other = await context.newPage();
  await other.goto(entry);
  await distribute(request, "v2");
  /* 新版は旧版と音源 1 つ・artwork を共有するため、取り直すものが無い */
  const fetched = downloads(context);
  await prepareOnline(first);
  expect(fetched).toEqual([]);
  await first.close();
  /* other が旧版を使っているため、新しいページの起動では昇格しない */
  const during = await context.newPage();
  await during.goto(entry);
  await expect(during.locator("#readiness")).toHaveText(saved);
  await expect(
    during.getByRole("button", { name: "Air study", exact: true }),
  ).toHaveCount(1);
  expect(await storedAssets(during)).toBe(3);
  await during.close();
  const next = await restart(other, context);
  await expect(next.locator("#readiness")).toHaveText(saved);
  await expect(
    next.getByRole("button", { name: "Air study", exact: true }),
  ).toHaveCount(0);
  await play(next, "Album crossfade");
  expect(await storedAssets(next)).toBe(2);
});

test("未完了の新版へは切り替えず、共有する実体を残した旧版で起動し、再準備で続きから取り直す", async ({
  page,
  context,
  request,
}) => {
  await distribute(request, "v2");
  await page.goto(entry);
  await prepareOnline(page);
  const current = await restart(page, context);
  await expect(current.locator("#readiness")).toHaveText(saved);
  await context.setOffline(false);
  await distribute(request, "v1");
  await context.route(secondAudio, (route) =>
    route.fulfill({ status: 503, body: "unavailable" }),
  );
  await openPreparation(current);
  await current.locator("#token").fill(fixtureToken);
  await current.locator("#prepare").click();
  await expect(current.locator("#preparation-detail")).toContainText(
    "音源を取得できませんでした（tone-second）",
  );
  const next = await restart(current, context);
  await expect(next.locator("#readiness")).toHaveText(saved);
  await expect(next.locator("#preparation-detail")).toContainText(
    "前の版を使っています",
  );
  await play(next, "Album crossfade");
  await context.setOffline(false);
  await context.unroute(secondAudio);
  const fetched = downloads(context);
  await prepareOnline(next);
  expect(fetched).toEqual(["/offline-player/audio/second.flac"]);
  const upgraded = await restart(next, context);
  await play(upgraded, "Air study");
});

(["missing", "corrupt"] as const).forEach((fault) => {
  test(`${fault}: 起動時に検出して準備完了とせず、オンライン再保存で欠けた実体だけを取り直す`, async ({
    page,
    context,
  }) => {
    await page.goto(entry);
    await prepareOnline(page);
    const current = await restart(page, context);
    await expect(current.locator("#readiness")).toHaveText(saved);
    await damage(current, fault);
    const next = await restart(current, context);
    await expect(next.locator("#readiness")).toHaveText(unready);
    await expect(next.locator("#preparation-detail")).toContainText(
      "音源の検証が完了しませんでした",
    );
    await next.getByRole("button", { name: "Air study", exact: true }).click();
    await expect(next.locator("#play-status")).toHaveText(
      "読み込みに失敗しました",
    );
    await expect(next.locator("#error")).toContainText(
      fault === "missing" ? "未保存" : "破損",
    );
    await expect(next.locator("#play")).toBeDisabled();
    await context.setOffline(false);
    const fetched = downloads(context);
    /* active と同じ版なので切り替えず、その場で修復する */
    await prepareOnline(next, saved);
    expect(fetched).toEqual(["/offline-player/audio/second.flac"]);
    const repaired = await restart(next, context);
    await expect(repaired.locator("#readiness")).toHaveText(saved);
    await play(repaired, "Air study");
  });
});

test("取得中の token 期限切れで止め、再入力後は検証済みの実体を取り直さない。診断に token と署名URLを出さない", async ({
  page,
  context,
}) => {
  const logged = logs(page);
  const fetched = downloads(context);
  await context.route(secondUrl, (route) =>
    route.fulfill({
      status: 401,
      contentType: "application/problem+json",
      body: "{}",
    }),
  );
  await page.goto(entry);
  await page.locator("#token").fill(fixtureToken);
  await page.locator("#prepare").click();
  await expect(page.locator("#preparation-detail")).toContainText(
    "トークンを入力し直してください",
  );
  expect(fetched).toEqual(["/offline-player/audio/first.flac"]);
  await expectNoSecrets(page, logged);
  expect(logged.some((text) => text.includes('"error":"unauthorized"'))).toBe(
    true,
  );
  await context.unroute(secondUrl);
  await prepareOnline(page);
  expect(fetched).toEqual([
    "/offline-player/audio/first.flac",
    "/offline-player/audio/second.flac",
  ]);
  await expectNoSecrets(page, logged);
});

test("token を保存せず、読み込み直すと入力し直しになる", async ({ page }) => {
  await page.goto(entry);
  await prepareOnline(page);
  const kept = await page.evaluate(() => ({
    local: Object.keys(localStorage),
    session: Object.keys(sessionStorage),
    url: location.href,
  }));
  expect(kept.local).toEqual([]);
  expect(kept.session).toEqual([]);
  expect(kept.url).not.toContain(fixtureToken);
  await page.reload();
  await openPreparation(page);
  await expect(page.locator("#token")).toHaveValue("");
});

test("準備済みの端末で再認証を求められたら、準備の画面に留まって入力し直せる", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await prepareOnline(page);
  const current = await restart(page, context);
  await expect(current.locator("#preparation")).toBeHidden();
  await context.setOffline(false);
  await context.route("**/api/v1/listening/package", (route) =>
    route.fulfill({
      status: 401,
      contentType: "application/problem+json",
      body: "{}",
    }),
  );
  await openPreparation(current);
  await current.locator("#token").fill(fixtureToken);
  await current.locator("#prepare").click();
  await expect(current.locator("#preparation-detail")).toContainText(
    "トークンを入力し直してください",
  );
  await expect(current.locator("#preparation")).toBeVisible();
  /* 再認証が要っても、準備済みの package で試聴を続けられる */
  await play(current, "Album crossfade");
});

test("署名URLの期限切れはその音源の失敗として示し、再試行で取り直す", async ({
  page,
  context,
}) => {
  const logged = logs(page);
  await context.route(secondAudio, (route) =>
    route.fulfill({ status: 403, body: "expired" }),
  );
  await page.goto(entry);
  await page.locator("#token").fill(fixtureToken);
  await page.locator("#prepare").click();
  await expect(page.locator("#preparation-detail")).toContainText(
    "取得URLの期限切れを含む",
  );
  await expectNoSecrets(page, logged);
  await context.unroute(secondAudio);
  await prepareOnline(page);
});

test("取得途中でページを閉じても、次の準備は保存済みの実体を取り直さずに続ける", async ({
  page,
  context,
}) => {
  const hold = new Promise<void>(() => undefined);
  await context.route(secondAudio, async () => {
    await hold;
  });
  await page.goto(entry);
  await page.locator("#token").fill(fixtureToken);
  await page.locator("#prepare").click();
  await expect.poll(() => storedAssets(page)).toBe(1);
  const next = await restart(page, context);
  /* 途中の pending は昇格せず、準備済みの package は無い */
  await expect(next.locator("#readiness")).toHaveText(unready);
  await context.unroute(secondAudio);
  await context.setOffline(false);
  const fetched = downloads(context);
  await prepareOnline(next);
  expect(fetched).toEqual(["/offline-player/audio/second.flac"]);
});

test("容量が足りなければ取得を始めず、必要量と空きを示す", async ({
  page,
  context,
}) => {
  await page.addInitScript(() => {
    navigator.storage.estimate = () =>
      Promise.resolve({ quota: 1_000, usage: 900 });
  });
  const fetched = downloads(context);
  await page.goto(entry);
  await page.locator("#token").fill(fixtureToken);
  await page.locator("#prepare").click();
  await expect(page.locator("#preparation-detail")).toContainText(
    "保存容量が不足しています（必要 0.2 MB / 空き 0.0 MB）",
  );
  expect(fetched).toEqual([]);
  expect(await storedAssets(page)).toBe(0);
});

test("このアプリの版で動かない配布物は保存せず、アプリの更新を求める", async ({
  page,
  context,
}) => {
  await context.route("**/api/v1/listening/package", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ...distributions.v1,
        compatibleAppVersion: {
          minInclusive: [1, 11, 0],
          maxExclusive: [2, 0, 0],
        },
      }),
    }),
  );
  const fetched = downloads(context);
  await page.goto(entry);
  await page.locator("#token").fill(fixtureToken);
  await page.locator("#prepare").click();
  await expect(page.locator("#preparation-detail")).toContainText(
    "このアプリの版では使えない配布物です",
  );
  expect(fetched).toEqual([]);
});

test("形の正しくない token では配布元へ問い合わせない", async ({ page }) => {
  const asked: string[] = [];
  page.on("request", (request) => {
    [request.url()]
      .filter((url) => url.includes("/api/"))
      .forEach((url) => {
        asked.push(url);
      });
  });
  await page.goto(entry);
  await page.locator("#token").fill("not-a-token");
  await page.locator("#prepare").click();
  await expect(page.locator("#preparation-detail")).toHaveText(
    "端末のトークンの形が正しくありません。",
  );
  expect(asked).toEqual([]);
});

test("音源が揃っていてもshell欠落を準備完了としない", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await prepareOnline(page);
  const current = await restart(page, context);
  await expect(current.locator("#readiness")).toHaveText(saved);
  await current.evaluate(async () => {
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
  await openPreparation(current);
  await current.locator("#inspect").click();
  await expect(current.locator("#readiness")).toHaveText(unready);
  await current.close();
  const next = await context.newPage();
  expect((await next.goto(entry))?.status()).toBe(503);
  await expect(next.locator("body")).toContainText("欠落・破損");
});

test("書体の欠落を準備完了とせず、通信なしの起動で字形を偽らない", async ({
  page,
  context,
}) => {
  await page.goto(entry);
  await prepareOnline(page);
  const current = await restart(page, context);
  await expect(current.locator("#readiness")).toHaveText(saved);
  await current.evaluate(async () => {
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
  await openPreparation(current);
  await current.locator("#inspect").click();
  await expect(current.locator("#readiness")).toHaveText(unready);
  const next = await restart(current, context);
  /* 画面そのものは残っているため開けるが、面は届かず、OS の書体で描かれる */
  await expect(next.locator("#readiness")).toHaveText(unready);
  await expect(next.locator("#preparation-detail")).toContainText(
    "アプリの保存が完了していません",
  );
  const typefaces = await loadTypefaces(next);
  expect(typefaces.filter((face) => face.status === "loaded")).toHaveLength(0);
  expect(await drawnWithTypeface(next, JAPANESE_SAMPLE)).toBe(false);
});
