import { test, expect } from "@playwright/test";
import type { Page, BrowserContext } from "@playwright/test";
import { mkdir } from "node:fs/promises";
const path = "/installation-poc/";
const prepare = async (page: Page): Promise<void> => {
  await page.goto(path);
  await page.getByRole("button", { name: "保存・更新を確認" }).click();
  await expect(page.locator("#status")).toHaveText(
    "アプリの保存を確認しました",
  );
  await page.reload();
  await expect
    .poll(() =>
      page.evaluate(() => navigator.serviceWorker.controller !== null),
    )
    .toBe(true);
};
const update = (page: Page): Promise<string> =>
  page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration("./");
    return new Promise<string>((resolve, reject) => {
      registration?.addEventListener(
        "updatefound",
        () => {
          const candidate = registration.installing;
          candidate?.addEventListener("statechange", () => {
            (["installed", "redundant"].includes(candidate.state)
              ? () => {
                  resolve(candidate.state);
                }
              : () => undefined)();
          });
        },
        { once: true },
      );
      void registration?.update().catch(reject);
    });
  });
const reopenOffline = async (
  context: BrowserContext,
  page: Page,
  version: string,
): Promise<Page> => {
  await page.close();
  await context.setOffline(true);
  const reopened = await context.newPage();
  const response = await reopened.goto(path);
  expect(response?.fromServiceWorker()).toBe(true);
  await expect(reopened.locator(".edition")).toHaveText(
    `OFFLINE STUDY / ${version}`,
  );
  await expect(reopened.locator("#status")).toHaveText(
    "アプリの保存を確認しました",
  );
  await expect(
    reopened.getByRole("button", { name: "画面を開き直す" }),
  ).toHaveCSS("min-height", "48px");
  return reopened;
};
test.beforeEach(async ({ request }) => {
  await request.post("/__control?version=v1");
});

test("saved HTML, JS and CSS boot in a new offline page", async ({
  page,
  context,
}) => {
  await prepare(page);
  const reopened = await reopenOffline(context, page, "v1");
  await mkdir("test-results", { recursive: true });
  await reopened.screenshot({
    path: "test-results/shell-offline.png",
    fullPage: true,
  });
  await reopened.setViewportSize({ width: 420, height: 900 });
  await reopened.screenshot({
    path: "test-results/shell-portrait.png",
    fullPage: true,
  });
  expect(
    await reopened.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await reopened.getByRole("button", { name: "画面を開き直す" }).click();
  await expect(reopened.locator("#status")).toHaveText(
    "アプリの保存を確認しました",
  );
});

(["http", "corrupt", "redirect"] as const).forEach((fault) => {
  test(`failed ${fault} update keeps old generation and a later retry activates`, async ({
    page,
    context,
    request,
  }) => {
    await prepare(page);
    await request.post(`/__control?version=v2&fault=${fault}`);
    expect(await update(page)).toBe("redundant");
    expect(
      await page.evaluate(
        async () =>
          (await caches.keys()).filter((key) =>
            key.startsWith("abservice-shell:"),
          ).length,
      ),
    ).toBe(1);
    const reopened = await reopenOffline(context, page, "v1");
    await context.setOffline(false);
    await request.post("/__control?version=v2");
    expect(await update(reopened)).toBe("installed");
    await expect(reopened.locator(".edition")).toContainText("v1");
    await reopenOffline(context, reopened, "v2");
  });
});

test("new version waits for all old tabs; retains old and unrelated caches", async ({
  page,
  context,
  request,
}) => {
  await prepare(page);
  await page.evaluate(async () => {
    await caches.open("unrelated-cache");
  });
  const second = await context.newPage();
  await second.goto(path);
  await request.post("/__control?version=v2");
  expect(await update(page)).toBe("installed");
  await page.close();
  await second.reload();
  await expect(second.locator(".edition")).toContainText("v1");
  expect(
    await second.evaluate(async () =>
      Boolean((await navigator.serviceWorker.getRegistration("./"))?.waiting),
    ),
  ).toBe(true);
  const reopened = await reopenOffline(context, second, "v2");
  const names = await reopened.evaluate(() => caches.keys());
  expect(
    names.filter((name) => name.startsWith("abservice-shell:")),
  ).toHaveLength(2);
  expect(names).toContain("unrelated-cache");
});

test("first installation failure never reports readiness and can be retried", async ({
  page,
  request,
}) => {
  await request.post("/__control?version=v1&fault=http");
  await page.goto(path);
  await page.getByRole("button", { name: "保存・更新を確認" }).click();
  await expect(page.locator("#status")).toContainText("取得できません");
  expect(
    await page.evaluate(async () =>
      (await caches.keys()).filter((key) => key.startsWith("abservice-shell:")),
    ),
  ).toHaveLength(0);
  expect(
    await page.evaluate(async () =>
      Boolean((await navigator.serviceWorker.getRegistration("./"))?.active),
    ),
  ).toBe(false);
  await request.post("/__control?version=v1");
  await page.getByRole("button", { name: "保存・更新を確認" }).click();
  await expect(page.locator("#status")).toHaveText(
    "アプリの保存を確認しました",
  );
});

test("unrelated navigation and API responses are not shell fallbacks or cached", async ({
  page,
}) => {
  await prepare(page);
  const response = await page.goto("/installation-poc/unknown");
  expect(response?.status()).toBe(404);
  expect(response?.fromServiceWorker()).toBe(false);
  expect(
    await page.evaluate(async () => {
      const names = await caches.keys();
      return Promise.all(
        names.map(async (name) =>
          (await (await caches.open(name)).keys()).map(
            (request) => request.url,
          ),
        ),
      );
    }),
  ).toEqual([
    expect.arrayContaining([
      expect.stringContaining("/index.html"),
      expect.stringContaining("/app.js"),
      expect.stringContaining("/style.css"),
    ]),
  ]);
  expect(
    await page.evaluate(
      async () => (await fetch("/installation-poc/api")).status,
    ),
  ).toBe(404);
});

test("missing cached shell fails closed without mixing in newer network content", async ({
  page,
  request,
}) => {
  await prepare(page);
  await page.evaluate(async () => {
    const name =
      (await caches.keys()).find((key) => key.startsWith("abservice-shell:")) ??
      "";
    const cache = await caches.open(name);
    const request = (await cache.keys()).find((key) =>
      key.url.endsWith("/index.html"),
    );
    await (request === undefined
      ? Promise.resolve(false)
      : cache.delete(request));
  });
  await request.post("/__control?version=v2");
  const response = await page.reload();
  expect(response?.status()).toBe(503);
  expect(response?.fromServiceWorker()).toBe(true);
  await expect(page.locator("body")).toContainText("欠落・破損");
});
